import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

// No network, k6 process or DB writes: run request validation against HTTP stubs.
const metrics = {};
class Metric {
    constructor(name) { this.samples = metrics[name] = []; }
    add(value, tags) { this.samples.push({ value, tags }); }
}
let mode = 'success';
const payloads = [];
const execution = { scenario: { name: 'vus_200', iterationInTest: 0, startTime: Date.now() }, vu: { idInTest: 1 } };
const context = vm.createContext({
    __ENV: { API_KEY: 'mock-only', SERVICE_ID: 'local-test', USER_IDS: 'u1,u2', RUN_ID: '11111111-1111-4111-8111-111111111111' },
    http: {
        expectedStatuses: status => status,
        post: (url, raw, options) => {
            const payload = JSON.parse(raw);
            payloads.push(payload);
            assert.equal(url, 'http://localhost:8080/api/v1/events');
            assert.equal(options.headers['X-API-Key'], 'mock-only');
            assert.equal(options.tags.test_group, payload.externalEventId.split('-').slice(-3, -2)[0]);
            return { status: mode === 'replay' ? 200 : 201, json: () => {
                if (mode === 'non-json') throw Error('invalid JSON');
                return { success: true, code: 'COMMON_201', data: { ...payload, eventId: 1, userId: mode === 'wrong-user' ? 'other' : payload.userId } };
            } };
        },
    },
    check: (response, checks) => Object.values(checks).every(fn => fn(response)),
    exec: execution, Counter: Metric, Rate: Metric, Trend: Metric,
});
const source = readFileSync(new URL('./event-common.js', import.meta.url), 'utf8')
    .replace(/^import .*;\r?\n/gm, '').replace(/\bexport /g, '');
vm.runInContext(source, context);
for (let i = 0; i < 1001; i++) {
    execution.scenario.iterationInTest = i;
    execution.vu.idInTest = i + 1;
    vm.runInContext('sendEvent()', context);
}
assert.equal(new Set(payloads.map(p => p.externalEventId)).size, 1001);
assert(payloads.every(p => p.externalEventId.length <= 100 && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(p.occurredAt)));
assert.equal(payloads[0].userId, 'u1');
assert.equal(payloads[1].userId, 'u2');
for (mode of ['replay', 'non-json', 'wrong-user']) {
    vm.runInContext('sendEvent()', context);
    assert.equal(metrics.events_created.at(-1).value, 0);
    assert.equal(metrics.event_error_rate.at(-1).value, true);
}
const summary = vm.runInContext("summarize({metrics: {'event_attempts{test_group:vus_200}': {values: {count: 120}}}}, 'stress-200', [{name: 'vus_200', seconds: 60}])", context);
assert(summary.stdout.includes('2.00'));
assert(Object.keys(summary).some(k => k.includes('results-stress-200-11111111')));
assert(!JSON.stringify(summary).includes('mock-only'));
const spike = readFileSync(new URL('./event-spike-test.js', import.meta.url), 'utf8')
    .replace(/^import .*;\r?\n/gm, '').replace('export default function ()', 'function spikeIteration()').replace(/\bexport /g, '');
vm.runInContext(spike, context);
mode = 'success';
assert.equal(vm.runInContext('sendEvent().success', context), true);
execution.instance = { vusActive: 50 };
vm.runInContext('Date.now = () => 1234567890000', context);
for (const [seconds, phase] of [[0, 'baseline'], [9.999, 'baseline'], [10, 'surge'], [10.999, 'surge'], [11, 'peak'], [20.999, 'peak'], [21, 'decline'], [21.999, 'decline'], [22, 'recovery'], [31.999, 'recovery'], [32, 'recovery'], [41.999, 'recovery']]) {
    execution.scenario.startTime = 1234567890000 - seconds * 1000;
    vm.runInContext('spikeIteration()', context);
    assert.equal(metrics.event_attempts.at(-1).tags.test_group, phase);
}
assert.equal(vm.runInContext('options.scenarios.spike.executor', context), 'ramping-vus');
assert.equal(vm.runInContext('options.scenarios.spike.startVUs', context), 50);
assert.equal(vm.runInContext('options.scenarios.spike.gracefulRampDown', context), '0s');
assert.equal(vm.runInContext('JSON.stringify(options.scenarios.spike.stages)', context), JSON.stringify([
    {duration:'10s',target:50},{duration:'1s',target:500},{duration:'10s',target:500},{duration:'1s',target:50},{duration:'20s',target:50},
]));
const attemptsBeforeTail = metrics.event_attempts.length;
execution.scenario.startTime = 1234567890000 - 42000;
vm.runInContext('spikeIteration()', context);
assert.equal(metrics.event_attempts.length, attemptsBeforeTail);
// Validate the extended profile independently without making network requests.
const extendedCalls = [];
const extendedExecution = { instance: { vusActive: 500 }, scenario: { startTime: 1234567890000 } };
const extended = vm.createContext({
    __ENV: { SPIKE_PEAK_SECONDS: '20', SPIKE_REPORT_TYPE: 'spike2' },
    exec: extendedExecution, Counter: Metric, Rate: Metric, Trend: Metric,
    groupThresholds: () => ({}), summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)'],
    sendEvent: phase => { extendedCalls.push(phase); return {success:true,status:201,errorCode:0,durationMs:1}; },
    summarize: (data, type, plan) => ({type, plan}),
});
vm.runInContext('Date.now = () => 1234567890000', extended);
vm.runInContext(spike, extended);
assert.equal(vm.runInContext('JSON.stringify(options.scenarios.spike.stages)', extended), JSON.stringify([
    {duration:'10s',target:50},{duration:'1s',target:500},{duration:'20s',target:500},{duration:'1s',target:50},{duration:'20s',target:50},
]));
for (const [seconds, phase] of [[0,'baseline'],[9.999,'baseline'],[10,'surge'],[10.999,'surge'],[11,'peak'],[30.999,'peak'],[31,'decline'],[31.999,'decline'],[32,'recovery'],[41.999,'recovery'],[42,'recovery'],[51.999,'recovery']]) {
    extendedExecution.scenario.startTime = 1234567890000 - seconds * 1000;
    vm.runInContext('spikeIteration()', extended);
    assert.equal(extendedCalls.at(-1), phase);
}
const extendedBeforeTail = extendedCalls.length;
extendedExecution.scenario.startTime = 1234567890000 - 52000;
vm.runInContext('spikeIteration()', extended);
assert.equal(extendedCalls.length, extendedBeforeTail);
assert.equal(vm.runInContext('handleSummary({}).type', extended), 'spike2');
assert.throws(() => vm.runInNewContext(source, { ...context, __ENV: { ...context.__ENV, API_BASE_URL: 'https://example.com' } }), /loopback/);
console.log('PASS: UTC payload, 1001 unique IDs, user allocation, replay/non-JSON/identity failures, report RPS/path/secrets, spike phase boundaries, loopback guard');
