import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
process.chdir(fileURLToPath(new URL('..', import.meta.url)));
const read = path => fs.readFileSync(path, 'utf8').replace(/^\uFEFF/, '');
const json = path => JSON.parse(read(path));
const write = (path, data) => fs.writeFileSync(path, data, 'utf8');
const runId = '7e3352c8-b82d-4034-8048-6f250fa5d430';
const stem = `load-tests/results-spike-${runId}`;
const data = json(`${stem}.json`);
const resources = json(`${stem}.resources.json`);
const db = json(`${stem}.db.json`);
const dbPhases = json(`${stem}.db-phases.json`);
const health = json(`${stem}.health.json`);
const execution = json(`${stem}.execution.json`);
const post = json(`${stem}.postcheck.json`);
const stress = json('load-tests/issue21-stress-comparison.json').runs;
const metric = name => data.metrics[name]?.values;
const slice = (name, phase) => metric(`${name}{test_group:${phase}}`);
const fmt = value => value == null ? 'N/A' : Number(value).toFixed(2);
const change = (next, previous) => 100 * (next / previous - 1);
assert.equal(resources.k6ExitCode, 0);
assert.equal(post.stoppedEarly, false);
assert.equal(post.authenticatedGetOk, true);
assert.equal(post.activeK6Count, 0);
assert.equal(data.userCount, 100);
const phases = data.spikePlan.map(plan => {
    const name = plan.name;
    const attempts = slice('event_attempts', name).count;
    const completed = slice('spike_completed_requests', name).count;
    const created = slice('events_created', name).count;
    const start = slice('spike_request_start_seconds', name);
    const vus = slice('spike_active_vus', name);
    assert(start.min >= plan.start && start.max < plan.end, `Phase boundary mismatch: ${name}`);
    assert(vus.max <= 500, 'Exceeded maximum 500 VUs');
    const stored = dbPhases.phases.find(item => item.phase === name)?.count ?? 0;
    assert.equal(stored, attempts, `Persisted run phase does not match attempts: ${name}`);
    const latency = slice('http_req_duration', name);
    return { ...plan, attempts, completed, clientConfirmed: created, interrupted: attempts - completed,
        startedRps: attempts / plan.seconds, confirmedRps: created / plan.seconds,
        averageMs: latency.avg, p95Ms: latency['p(95)'],
        successPercent: slice('event_success_rate', name).rate * 100,
        errorPercent: slice('event_error_rate', name).rate * 100,
        networkErrors: slice('spike_network_errors', name).count,
        httpErrors: slice('spike_http_errors', name).count,
        connectionRefused: slice('spike_connection_refused', name).count,
        firstRequestSecond: start.min, lastRequestSecond: start.max,
        activeVuMin: vus.min, activeVuMedian: vus.med, activeVuMax: vus.max, dbEvents: stored,
    };
});
const timeline = Array.from({ length: 42 }, (_, second) => {
    const v = metric(`spike_active_vus{second:${second}}`);
    return { second, min: v?.min ?? null, median: v?.med ?? null, max: v?.max ?? null };
});
assert(timeline.every(item => item.min != null), 'Missing per-second VU observations');
assert(phases.find(p => p.name === 'recovery').activeVuMin === 50 && phases.find(p => p.name === 'recovery').activeVuMax === 50);
const recovery = ['early', 'late'].map(window => {
    const latency = metric(`spike_recovery_duration{window:${window}}`);
    const count = metric(`spike_recovery_completed{window:${window}}`).count;
    return { window, start: window === 'early' ? 22 : 32, end: window === 'early' ? 32 : 42,
        completed: count, confirmedRps: count / 10,
        averageMs: latency.avg, p95Ms: latency['p(95)'],
        successPercent: metric(`spike_recovery_success{window:${window}}`).rate * 100,
        errorPercent: metric(`spike_recovery_errors{window:${window}}`).rate * 100 };
});
const baseline = phases[0];
const late = recovery[1];
const recoveryAssessment = {
    lateAverageChangePercent: change(late.averageMs, baseline.averageMs),
    lateP95ChangePercent: change(late.p95Ms, baseline.p95Ms),
    lateThroughputChangePercent: change(late.confirmedRps, baseline.confirmedRps),
    result: 'Errors and p95 recovered; mean latency remained above baseline. Do not claim full latency/throughput recovery from this single run.',
};
const before = db.snapshots.find(s => s.phase === 'before_load');
const beforeAuth = db.snapshots.find(s => s.phase === 'before_auth');
const after = db.snapshots.find(s => s.phase === 'after_load');
const attempts = metric('event_attempts').count;
const confirmed = metric('events_created').count;
const completed = metric('spike_completed_requests').count;
assert.equal(attempts, phases.reduce((sum, p) => sum + p.attempts, 0));
assert.equal(attempts, after.run);
assert.equal(after.run, after.distinctRun);
assert.equal(after.duplicateKeys, 0);
assert.equal(after.total - before.total, after.run);
assert.equal(after.service - before.service, after.run);
assert.equal(after.run - confirmed, attempts - completed);
const dbVerification = { beforeAuth, before, after, loadAdded: after.total - before.total,
    includingAuthAdded: after.total - beforeAuth.total, interrupted: attempts - completed,
    persistedWithoutClientConfirmation: after.run - confirmed, allAttemptsReconciled: true };
const stats = samples => {
    const fields = ['systemCpuPercent', 'serverCpuPercent', 'k6CpuPercent', 'systemUsedMemoryMiB', 'systemAvailableMemoryMiB', 'serverWorkingSetMiB', 'serverPrivateMiB', 'k6WorkingSetMiB'];
    return { sampleCount: samples.length, ...Object.fromEntries(fields.map(field => {
        const values = samples.map(s => s[field]).filter(v => v != null);
        return [field, values.length ? { average: values.reduce((a, b) => a + b, 0) / values.length, min: Math.min(...values), max: Math.max(...values) } : null];
    })) };
};
const samples = resources.samples.filter(s => s.elapsedSeconds <= 42);
const resourceSummary = stats(samples);
const resourcesByApproximatePhase = Object.fromEntries(phases.map(p => [p.name, stats(samples.filter(s => s.elapsedSeconds >= p.start && s.elapsedSeconds < p.end))]));
// Decode the self-contained HTML and compare its cumulative metrics with the raw k6 summary.
const htmlPath = 'load-tests/issue21-spike-dashboard.html';
const encoded = read(htmlPath).match(/<script id="data"[^>]*>([\s\S]*?)<\/script>/)?.[1];
assert(encoded, 'Missing dashboard data');
const events = zlib.gunzipSync(Buffer.from(encoded.trim(), 'base64')).toString().trim().split('\n').map(JSON.parse);
const names = [...new Set(events.filter(e => e.event === 'metric').flatMap(e => Object.keys(e.data)))].sort();
const cumulative = events.filter(e => e.event === 'cumulative').at(-1).data;
const htmlMetrics = Object.fromEntries(names.map((name, i) => [name, cumulative[i]]));
for (const name of ['event_attempts', 'events_created', 'spike_completed_requests', 'spike_network_errors', 'spike_http_errors', 'spike_connection_refused']) assert.equal(htmlMetrics[name][0], metric(name).count, `HTML mismatch: ${name}`);
assert(Math.abs(htmlMetrics.http_req_duration[0] - metric('http_req_duration').avg) < 0.1);
assert(Math.abs(htmlMetrics.http_req_duration[5] - metric('http_req_duration')['p(95)']) < 0.1);
const csvLines = read(`${stem}.resources.csv`).trim().split(/\r?\n/);
assert.equal(csvLines.length - 1, resources.samples.length);
const sourceHashes = Object.fromEntries(['load-tests/event-spike-test.js', 'load-tests/event-common.js', 'load-tests/run-issue21-spike.ps1'].map(path => [path, crypto.createHash('sha256').update(fs.readFileSync(path)).digest('hex')]));
const notes = [
    'Pre-Redis Baseline: one run, original local API authentication and 100 user IDs, existing server/DB tuning, unchanged persisted records. No Redis dependency or integration was found in build.gradle/src/main. New events are test-generated inserts only.',
    'One continuous ramping-vus scenario: 50 VU for 10s, ramp to 500 over 1s, hold 500 for 10s, ramp to 50 over 1s, hold 50 for 20s. All request-start samples fall inside their intended phase boundaries; all 42 seconds have VU samples.',
    'Scheduling deviations: initial activation samples range from 14 to 50 active VUs; at exactly the peak boundary one sample observes 499 before 500. Peak median/max are 500; recovery min/max are exactly 50. These are observed scheduler transitions, not a claim of perfect instantaneous VU activation.',
    'gracefulRampDown=0s enforces the 1s decline. It intentionally interrupted 450 requests (337 attributed to peak, 113 to decline). They are not counted as HTTP/network errors and are excluded from completed-response success/error denominators. All 450 nevertheless persisted in the DB.',
    'RPS per phase is request starts divided by planned phase seconds; client-confirmed/s is shown separately. Responses are attributed to request start time and may complete in the next phase or final 10s grace. During a 1s transition RPS can exceed steady-state throughput. Per-phase request starts are not per-second completions.',
    'Zero observed HTTP errors, network errors and connection refusals; 100% success is among completed responses only. k6 thresholds PASS does not mean zero interrupted work or an independently defined latency SLA.',
    'Recovery early/late windows are 22-32s and 32-42s based on request starts. p95 and error rate recovered; late mean latency was still above baseline, and late confirmed throughput was lower. This is partial recovery evidence, not proof of complete recovery.',
    'CPU uses all 8 logical CPUs as 100%. Resource averages are unweighted samples with irregular intervals (1s sleep plus CIM overhead). Resource phase allocation uses collector elapsed time including startup and is approximate; exact phase timing/VU checks use k6 scenario-relative telemetry.',
    'k6 and Spring Boot run on the same Windows PC with MySQL/Docker and other processes, so CPU and memory competition affect the result. Dashboard aggregation is 1s instead of the Stress tests 5s, with extra phase/VU telemetry and occasional authenticated health GETs; these add monitoring overhead.',
    'Stress 500 VU begins at 500 immediately and runs 30s, while Spike is warmed at 50 VU and holds 500 for 10s. Stress had 262 startup connection refusals; Spike has none. This difference does not establish improved server capacity or a Redis benefit. Stress 1000 VU is an aborted memory-pressure observation only.',
    'Safety limits: free physical RAM below 64 MiB, original Java process exit, failed/10s-timeout authenticated health GET, or 75s process deadline trigger stopping only k6. No safety stop or retry was needed in this run. Dashboard HTTP is disabled (port=-1) to avoid browser-connected exit delays.',
];
const report = { label: 'Issue #21 pre-Redis Spike Baseline', runId, status: 'COMPLETED',
    actualMetricSeconds: data.state.testRunDurationMs / 1000, execution, phases, timeline, recovery,
    recoveryAssessment, resourceSummary, resourcesByApproximatePhase, dbVerification, health, post,
    totals: { attempts, completed, confirmed, interrupted: attempts - completed, actualCompletedRps: metric('http_reqs').rate,
        httpErrors: metric('spike_http_errors').count, networkErrors: metric('spike_network_errors').count, connectionRefused: metric('spike_connection_refused').count },
    stressReference: stress, htmlJsonMatch: true, sourceHashes, notes };
const table = (header, rows) => [header, '|' + header.split('|').slice(1, -1).map(() => '---').join('|') + '|', ...rows.map(row => `| ${row.join(' | ')} |`)].join('\n');
const body = [
    '# Issue #21: pre-Redis Spike Baseline', '', `RUN_ID: ${runId}`, `Status: COMPLETED, k6 exit 0, actual metric time ${fmt(report.actualMetricSeconds)}s.`, '',
    table('| Phase | Seconds | Starts/s | Confirmed/s | Avg ms | p95 ms | Success % | Error % | Interrupted |', phases.map(p => [p.name, `${p.start}-${p.end}`, fmt(p.startedRps), fmt(p.confirmedRps), fmt(p.averageMs), fmt(p.p95Ms), fmt(p.successPercent), fmt(p.errorPercent), p.interrupted])), '',
    table('| Phase | First request s | Last request s | Active VU min / median / max | DB events |', phases.map(p => [p.name, p.firstRequestSecond, p.lastRequestSecond, `${p.activeVuMin} / ${p.activeVuMedian} / ${p.activeVuMax}`, p.dbEvents])), '',
    table('| Recovery | Confirmed/s | Avg ms | p95 ms | Success % | Error % |', recovery.map(r => [r.window, fmt(r.confirmedRps), fmt(r.averageMs), fmt(r.p95Ms), fmt(r.successPercent), fmt(r.errorPercent)])), '',
    `Late recovery vs baseline: mean ${fmt(recoveryAssessment.lateAverageChangePercent)}%, p95 ${fmt(recoveryAssessment.lateP95ChangePercent)}%, confirmed/s ${fmt(recoveryAssessment.lateThroughputChangePercent)}%.`, '',
    table('| Resource | Sample average | Sample maximum |', Object.entries(resourceSummary).filter(([name]) => name !== 'sampleCount').map(([name, s]) => [name, fmt(s.average), fmt(s.max)])), '',
    `Resource samples: ${resourceSummary.sampleCount}; minimum available physical memory ${fmt(resourceSummary.systemAvailableMemoryMiB.min)} MiB.`, '',
    table('| DB phase | Total | Service | Run | Distinct run | Duplicate groups |', db.snapshots.map(s => [s.phase, s.total, s.service, s.run, s.distinctRun, s.duplicateKeys])), '',
    `Load inserted ${dbVerification.loadAdded} events; including authentication preflight ${dbVerification.includingAuthAdded}. Client confirmed ${confirmed}, interrupted ${attempts - completed}; every started request is present once in DB.`, '',
    table('| Stress VU | Status | k6 elapsed RPS | Avg ms | p95 ms | Success % | Network errors |', stress.map(s => [s.vus, s.stoppedEarly ? 'ABORTED' : 'Completed', fmt(s.actualRps), fmt(s.averageMs), fmt(s.p95Ms), fmt(s.successPercent), s.networkFailures])), '',
    `Spike peak: ${fmt(phases[2].startedRps)} starts/s, ${fmt(phases[2].confirmedRps)} confirmed/s, ${fmt(phases[2].averageMs)}ms mean, ${fmt(phases[2].p95Ms)}ms p95. Do not equate this start-attributed phase rate to the Stress elapsed-time completion rate.`, '',
    ...notes.map(note => `- ${note}`), '',
    'Artifacts are under load-tests/: issue21-spike-dashboard.html, issue21-spike-baseline.json, and results-spike-' + runId + '.* (raw Markdown/JSON, resources CSV/JSON, DB snapshots and phase counts, execution, health, postcheck, logs).', '',
    'Use this exact workload, measurement definitions, source hashes and shared-PC caveats for a later Redis comparison. A single run is an exploratory baseline, not a reproducible capacity limit or proof that Redis will resolve the bottleneck.', '',
];
write('load-tests/issue21-spike-baseline.json', JSON.stringify(report, null, 2) + '\n');
write('load-tests/issue21-spike-baseline.md', body.join('\n'));
write('load-tests/issue21-spike-timeline.json', JSON.stringify({ runId, spikePlan: data.spikePlan, timeline }, null, 2) + '\n');
const rawMdPath = `${stem}.md`;
const rawMd = read(rawMdPath);
if (!rawMd.startsWith('> Interpretation')) write(rawMdPath, '> Interpretation: success/error rates exclude 450 requests interrupted by the configured 0s ramp-down grace. Those requests all persisted. RPS is request-start count / phase seconds. See issue21-spike-baseline.md for completed counts, timing and recovery analysis.\n\n' + rawMd);
const artifacts = [htmlPath, 'load-tests/issue21-spike-baseline.md', 'load-tests/issue21-spike-baseline.json', 'load-tests/issue21-spike-timeline.json', ...['md', 'json', 'resources.csv', 'resources.json', 'db.json', 'db-phases.json', 'execution.json', 'health.json', 'postcheck.json', 'stdout.log', 'stderr.log'].map(ext => `${stem}.${ext}`)];
for (const path of artifacts) {
    assert(fs.existsSync(path), `Missing artifact: ${path}`);
    if (path.endsWith('.json')) json(path);
    if (!path.endsWith('.stderr.log')) assert(fs.statSync(path).size > 0, `Empty artifact: ${path}`);
}
console.log(JSON.stringify({ status: report.status, totals: report.totals, phases, recovery, recoveryAssessment, resourceSummary, htmlJsonMatch: true, artifactCount: artifacts.length }, null, 2));
