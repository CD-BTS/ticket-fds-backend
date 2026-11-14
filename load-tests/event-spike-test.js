import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { groupThresholds, sendEvent, summarize, summaryTrendStats } from './event-common.js';

// One continuous ramping scenario keeps the VU population across phase boundaries.
const peakSeconds = Number(__ENV.SPIKE_PEAK_SECONDS || 10);
if (![10, 20].includes(peakSeconds)) throw new Error('SPIKE_PEAK_SECONDS must be 10 or 20');
const reportType = __ENV.SPIKE_REPORT_TYPE || 'spike';
if (!['spike', 'spike2'].includes(reportType)) throw new Error('Invalid SPIKE_REPORT_TYPE');
const peakEnd = 11 + peakSeconds;
const recoveryStart = peakEnd + 1;
const totalSeconds = recoveryStart + 20;
export const spikePlan = [
    { name: 'baseline', start: 0, end: 10, seconds: 10, from: 50, to: 50 },
    { name: 'surge', start: 10, end: 11, seconds: 1, from: 50, to: 500 },
    { name: 'peak', start: 11, end: peakEnd, seconds: peakSeconds, from: 500, to: 500 },
    { name: 'decline', start: peakEnd, end: recoveryStart, seconds: 1, from: 500, to: 50 },
    { name: 'recovery', start: recoveryStart, end: totalSeconds, seconds: 20, from: 50, to: 50 },
];
const phaseStarts = new Trend('spike_request_start_seconds');
const activeVus = new Trend('spike_active_vus');
const completed = new Counter('spike_completed_requests');
const networkErrors = new Counter('spike_network_errors');
const httpErrors = new Counter('spike_http_errors');
const refused = new Counter('spike_connection_refused');
const recoveryDuration = new Trend('spike_recovery_duration', true);
const recoverySuccess = new Rate('spike_recovery_success');
const recoveryErrors = new Rate('spike_recovery_errors');
const recoveryCompleted = new Counter('spike_recovery_completed');
const thresholds = groupThresholds(spikePlan);
for (const phase of spikePlan) {
    for (const metric of ['spike_request_start_seconds', 'spike_active_vus']) thresholds[`${metric}{test_group:${phase.name}}`] = ['p(95)>=0'];
    for (const metric of ['spike_completed_requests', 'spike_network_errors', 'spike_http_errors', 'spike_connection_refused']) thresholds[`${metric}{test_group:${phase.name}}`] = ['count>=0'];
}
for (let second = 0; second < totalSeconds; second++) thresholds[`spike_active_vus{second:${second}}`] = ['p(95)>=0'];
for (const window of ['early', 'late']) {
    thresholds[`spike_recovery_duration{window:${window}}`] = ['p(95)>=0'];
    thresholds[`spike_recovery_success{window:${window}}`] = ['rate>=0.99'];
    thresholds[`spike_recovery_errors{window:${window}}`] = ['rate<=0.01'];
    thresholds[`spike_recovery_completed{window:${window}}`] = ['count>=0'];
}
export const options = {
    scenarios: { spike: {
        executor: 'ramping-vus', startVUs: 50,
        stages: spikePlan.map(phase => ({ duration: `${phase.seconds}s`, target: phase.to })),
        // Enforce the one-second decline; reconcile interrupted requests with DB counts.
        gracefulRampDown: '0s', gracefulStop: '10s',
    } },
    thresholds, summaryTrendStats,
};
export default function () {
    const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
    if (elapsed >= totalSeconds) return; // Complete outstanding work only during the final grace interval.
    const phase = spikePlan.find(item => elapsed >= item.start && elapsed < item.end);
    const tags = { test_group: phase.name };
    phaseStarts.add(elapsed, tags);
    activeVus.add(exec.instance.vusActive, { ...tags, second: String(Math.floor(elapsed)) });
    const result = sendEvent(phase.name);
    completed.add(1, tags);
    networkErrors.add(result.status === 0 ? 1 : 0, tags);
    httpErrors.add(result.status !== 0 && result.status !== 201 ? 1 : 0, tags);
    refused.add(result.status === 0 && result.errorCode === 1212 ? 1 : 0, tags);
    if (phase.name === 'recovery') {
        const recoveryTags = { window: elapsed < recoveryStart + 10 ? 'early' : 'late' };
        recoveryDuration.add(result.durationMs, recoveryTags);
        recoverySuccess.add(result.success, recoveryTags);
        recoveryErrors.add(!result.success, recoveryTags);
        recoveryCompleted.add(1, recoveryTags);
    }
}
export function handleSummary(data) {
    data.spikePlan = spikePlan;
    data.phaseAttribution = 'Request start time; latency/errors counted after response. Interrupted work is excluded from response rates.';
    data.rampDownPolicy = '0s grace: 50 active VUs after decline; interrupted requests may still commit on the server.';
    return summarize(data, reportType, spikePlan);
}
