import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
process.chdir(fileURLToPath(new URL('..', import.meta.url)));
const read = p => fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');
const json = p => JSON.parse(read(p));
const write = (p, value) => fs.writeFileSync(p, value, 'utf8');
const runId = 'cb71518d-91c1-4a3c-ae67-31147d4792ec';
const stem = `load-tests/results-spike2-${runId}`;
const data = json(`${stem}.json`);
const resources = json(`${stem}.resources.json`);
const db = json(`${stem}.db.json`);
const dbPhases = json(`${stem}.db-phases.json`);
const pre = json(`${stem}.precheck.json`);
const post = json(`${stem}.postcheck.json`);
const health = json(`${stem}.health.json`);
const execution = json(`${stem}.execution.json`);
const previous = json('load-tests/issue21-spike-baseline.json');
const abort = fs.existsSync(`${stem}.abort.json`) ? json(`${stem}.abort.json`) : null;
const metric = name => data.metrics[name]?.values;
const slice = (name, phase) => metric(`${name}{test_group:${phase}}`);
const percentChange = (next, old) => next == null || !old ? null : 100 * (next / old - 1);
const actualSeconds = data.state.testRunDurationMs / 1000;
const status = abort || post.stoppedEarly ? 'ABORTED' : actualSeconds >= 52 && [0,99].includes(resources.k6ExitCode) ? 'COMPLETED' : 'INCOMPLETE';
assert.equal(data.userCount, 100);
assert.deepEqual(data.spikePlan.map(p => [p.start,p.end,p.to]), [[0,10,50],[10,11,500],[11,31,500],[31,32,50],[32,52,50]]);
const phases = data.spikePlan.map(plan => {
    const n = plan.name, attempts = slice('event_attempts', n)?.count ?? 0;
    const completed = slice('spike_completed_requests', n)?.count ?? 0;
    const confirmed = slice('events_created', n)?.count ?? 0;
    const latency = slice('http_req_duration', n);
    const timing = slice('spike_request_start_seconds', n), vus = slice('spike_active_vus', n);
    if (attempts) assert(timing.min >= plan.start && timing.max < plan.end, `Unexpected timing: ${n}`);
    if (vus) assert(vus.max <= 500, 'Exceeded maximum 500 VU');
    const observedSeconds = Math.max(0, Math.min(plan.end, actualSeconds) - plan.start);
    const seconds = status === 'COMPLETED' ? plan.seconds : observedSeconds;
    const networkErrors = slice('spike_network_errors', n)?.count ?? 0;
    const httpErrors = slice('spike_http_errors', n)?.count ?? 0;
    return {...plan, attempts, completed, confirmed, interrupted: attempts - completed,
        startedRps: seconds ? attempts / seconds : null, confirmedRps: seconds ? confirmed / seconds : null,
        averageMs: latency?.avg ?? null, p95Ms: latency?.['p(95)'] ?? null,
        successPercent: completed ? 100 * confirmed / completed : null,
        errorPercent: completed ? 100 * (completed-confirmed) / completed : null,
        networkErrors, httpErrors, connectionRefused: slice('spike_connection_refused', n)?.count ?? 0,
        responseValidationFailures: completed - confirmed - networkErrors - httpErrors,
        firstRequestSecond: timing?.min ?? null, lastRequestSecond: timing?.max ?? null,
        activeVuMin: vus?.min ?? null, activeVuMedian: vus?.med ?? null, activeVuMax: vus?.max ?? null,
        dbEvents: dbPhases.phases.find(p => p.phase === n)?.count ?? 0};
});
const timeline = Array.from({length:52}, (_, second) => {
    const v = metric(`spike_active_vus{second:${second}}`);
    return {second,min:v?.min ?? null,median:v?.med ?? null,max:v?.max ?? null};
});
if (status === 'COMPLETED') assert(timeline.every(s => s.min != null), 'Missing scenario-second observations');
const recovery = ['early','late'].map(window => {
    const d = metric(`spike_recovery_duration{window:${window}}`);
    const completed = metric(`spike_recovery_completed{window:${window}}`)?.count ?? 0;
    return {window,start:window === 'early' ? 32 : 42,end:window === 'early' ? 42 : 52,completed,
        confirmedRps:status === 'COMPLETED' ? completed/10 : null,
        averageMs:d?.avg ?? null,p95Ms:d?.['p(95)'] ?? null,
        successPercent:completed ? metric(`spike_recovery_success{window:${window}}`).rate*100 : null,
        errorPercent:completed ? metric(`spike_recovery_errors{window:${window}}`).rate*100 : null};
});
const baseline = phases[0], late = recovery[1];
const recoveryAssessment = {
    lateAverageChangePercent:percentChange(late.averageMs,baseline.averageMs),
    lateP95ChangePercent:percentChange(late.p95Ms,baseline.p95Ms),
    lateThroughputChangePercent:percentChange(late.confirmedRps,baseline.confirmedRps),
    lateErrorPercent:late.errorPercent,
};
const fields = ['systemCpuPercent','serverCpuPercent','k6CpuPercent','serverWorkingSetMiB','serverPrivateMiB','k6WorkingSetMiB','systemUsedMemoryMiB','systemAvailableMemoryMiB'];
const samples = resources.samples.filter(s => s.elapsedSeconds <= 52);
const resourceSummary = {sampleCount:samples.length,...Object.fromEntries(fields.map(field => {
    const values = samples.map(s => s[field]).filter(v => v != null);
    return [field,values.length ? {average:values.reduce((a,b)=>a+b,0)/values.length,min:Math.min(...values),max:Math.max(...values)} : null];
}))};
const beforeAuth = db.snapshots.find(s => s.phase === 'before_auth');
const before = db.snapshots.find(s => s.phase === 'before_load');
const after = db.snapshots.find(s => s.phase === 'after_load');
const sum = field => phases.reduce((total,p) => total + p[field],0);
const totals = {attempts:sum('attempts'),completed:sum('completed'),confirmed:sum('confirmed'),interrupted:sum('interrupted'),httpErrors:sum('httpErrors'),networkErrors:sum('networkErrors'),connectionRefused:sum('connectionRefused'),responseValidationFailures:sum('responseValidationFailures'),actualCompletedRps:metric('http_reqs').rate};
assert.equal(totals.attempts,metric('event_attempts').count);
assert.equal(totals.confirmed,metric('events_created').count);
const dbVerification = {beforeAuth,before,after,loadAdded:after.total-before.total,serviceAdded:after.service-before.service,
    includingAuthAdded:after.total-beforeAuth.total,duplicates:after.duplicateKeys,
    persistedWithoutClientConfirmation:after.run-totals.confirmed,
    persistedExtraMatchesInterrupted:after.run-totals.confirmed === totals.interrupted,
    uniqueRunMatchesRun:after.run === after.distinctRun,
    loadDeltaMatchesRun:after.total-before.total === after.run,
    attemptsMinusStored:totals.attempts-after.run};
const comparisons = phases.map(p => {
    const old = previous.phases.find(o=>o.name === p.name);
    return {phase:p.name,first:{seconds:old.seconds,startedRps:old.startedRps,confirmedRps:old.confirmedRps,averageMs:old.averageMs,p95Ms:old.p95Ms,errorPercent:old.errorPercent,interrupted:old.interrupted},second:p,
        startedRpsChangePercent:percentChange(p.startedRps,old.startedRps),confirmedRpsChangePercent:percentChange(p.confirmedRps,old.confirmedRps),averageChangePercent:percentChange(p.averageMs,old.averageMs),p95ChangePercent:percentChange(p.p95Ms,old.p95Ms)};
});
const htmlPath = 'load-tests/issue21-spike2-dashboard.html';
const encoded = read(htmlPath).match(/<script id="data"[^>]*>([\s\S]*?)<\/script>/)?.[1];
assert(encoded,'Missing HTML dashboard data');
const events = zlib.gunzipSync(Buffer.from(encoded.trim(),'base64')).toString().trim().split('\n').map(JSON.parse);
const names = [...new Set(events.filter(e=>e.event==='metric').flatMap(e=>Object.keys(e.data)))].sort();
const cumulative = events.filter(e=>e.event==='cumulative').at(-1).data;
const hm = Object.fromEntries(names.map((n,i)=>[n,cumulative[i]]));
for (const name of ['event_attempts','events_created','spike_completed_requests','spike_network_errors','spike_http_errors','spike_connection_refused']) assert.equal(hm[name]?.[0] ?? 0,metric(name)?.count ?? 0,`HTML/JSON mismatch: ${name}`);
assert(Math.abs(hm.http_req_duration[0]-metric('http_req_duration').avg)<0.1);
assert(Math.abs(hm.http_req_duration[5]-metric('http_req_duration')['p(95)'])<0.1);
assert.equal(read(`${stem}.resources.csv`).trim().split(/\r?\n/).length-1,resources.samples.length);
const sourceHashes = Object.fromEntries(['load-tests/event-spike-test.js','load-tests/event-common.js','load-tests/run-issue21-spike2.ps1'].map(path=>[path,crypto.createHash('sha256').update(fs.readFileSync(path)).digest('hex')]));
const notes = [
    'Exactly one second Spike execution, maximum 500 VU. Same event API authentication, 100 users, DB verification, resource sampling, dashboard options and zero-second ramp-down grace. First-run artifacts are preserved.',
    'Scenario: baseline 0-10s (50 VU), surge 10-11s (50 to 500), peak 11-31s (500), decline 31-32s (500 to 50), recovery 32-52s (50). Request-start timing and active-VU observations are saved for each phase and scenario second.',
    'RPS per phase means request starts / phase seconds. Confirmed/s is client-confirmed successes / phase seconds. Both attribute responses to start phase and may include responses completed after phase end. Whole-test k6 elapsed-time RPS is a different metric.',
    'Zero-second ramp-down grace preserves the one-second decline by cancelling retiring VU requests. Interrupted attempts are excluded from completed-response success/error/latency metrics and must not be reported as server HTTP or network failures. DB phase counts determine whether they nevertheless persisted.',
    'If safety-stopped, report partial observations only; no retry. Safety thresholds remain available physical RAM below 64 MiB, Java process exit, authenticated health GET failure or 10s timeout. Process deadline is 85s to accommodate the longer schedule and reporting.',
    'k6 and Spring Boot share this 8-logical-CPU Windows PC with MySQL/Docker and other programs. CPU competition, memory pressure, different pre-run state and warm caches confound comparisons. This single longer-peak run cannot isolate a causal duration effect or establish server-only capacity.',
    'Resource statistics are unweighted sample averages/maxima. One-second sleep plus CIM overhead gives irregular sample intervals. Memory is resident/private committed memory, not JVM heap; CPU is normalized to all logical CPUs.',
    'Recovery is compared both with this run baseline and with the first-run recovery windows. Mean, p95, throughput and errors must be considered separately; no arbitrary recovery latency SLA was introduced.',
    'Existing DB records and server performance settings were not modified. Authentication preflight and workload add new events; authenticated health GETs do not. No Redis or higher VU test was introduced.',
];
const peakComparison = comparisons.find(c=>c.phase==='peak');
const findings = [
    `Status ${status}; HTTP errors ${totals.httpErrors}, network errors ${totals.networkErrors}, connection refusals ${totals.connectionRefused}.`,
    `Longer peak vs first run: starts/s ${percentChange(phases[2].startedRps,previous.phases[2].startedRps).toFixed(2)}%; client-confirmed/s ${peakComparison.confirmedRpsChangePercent.toFixed(2)}%; average latency ${peakComparison.averageChangePercent.toFixed(2)}%; p95 ${peakComparison.p95ChangePercent.toFixed(2)}%. Single-run/shared-host confounders prevent attributing all differences solely to the longer peak.`,
    `Interrupted response confirmations ${totals.interrupted}; persisted without client confirmation ${dbVerification.persistedWithoutClientConfirmation}. They match=${dbVerification.persistedExtraMatchesInterrupted}; duplicate key groups ${dbVerification.duplicates}. Cancellation is not counted as server HTTP/network failure.`,
    `Late recovery vs own initial baseline: average latency ${recoveryAssessment.lateAverageChangePercent?.toFixed(2)}%; p95 ${recoveryAssessment.lateP95ChangePercent?.toFixed(2)}%; throughput ${recoveryAssessment.lateThroughputChangePercent?.toFixed(2)}%; error rate ${late.errorPercent}%. Evaluate these separately rather than claiming complete recovery.`,
];
const report = {runId,previousRunId:previous.runId,status,k6ExitCode:resources.k6ExitCode,actualMetricSeconds:actualSeconds,pre,execution,post,health,abort,phases,timeline,recovery,recoveryAssessment,totals,resourceSummary,dbVerification,comparisons,previousResourceSummary:previous.resourceSummary,previousRecovery:previous.recovery,htmlJsonMatch:true,sourceHashes,findings,notes};
const fmt = n => n == null ? 'N/A' : Number(n).toFixed(2);
const table = (head,rows) => [head,'|'+head.split('|').slice(1,-1).map(()=>'---').join('|')+'|',...rows.map(r=>'| '+r.join(' | ')+' |')].join('\n');
const body = [
    '# Issue #21: second Spike Test (20s peak)', '', `RUN_ID: ${runId}`, `Status: ${status}; k6 exit ${resources.k6ExitCode}; metric duration ${fmt(actualSeconds)}s.`, '',
    ...findings.map(f=>'- '+f), '',
    `Pre-run: system CPU ${fmt(pre.systemCpuPercent)}%; available RAM ${fmt(pre.systemAvailableMemoryMiB)} MiB; authenticated server GET OK=${pre.authenticatedGetOk}.`, '',
    table('| Phase | Seconds | Starts/s | Confirmed/s | Avg ms | p95 ms | Success % | Error % | HTTP errors | Network errors | Refused | Interrupted |',phases.map(p=>[p.name,`${p.start}-${p.end}`,fmt(p.startedRps),fmt(p.confirmedRps),fmt(p.averageMs),fmt(p.p95Ms),fmt(p.successPercent),fmt(p.errorPercent),p.httpErrors,p.networkErrors,p.connectionRefused,p.interrupted])), '',
    table('| Phase | First request s | Last request s | Active VU min / median / max | DB events |',phases.map(p=>[p.name,p.firstRequestSecond,p.lastRequestSecond,`${p.activeVuMin} / ${p.activeVuMedian} / ${p.activeVuMax}`,p.dbEvents])), '',
    table('| Recovery window | Confirmed/s | Avg ms | p95 ms | Error % |',recovery.map(r=>[`${r.window} (${r.start}-${r.end}s)`,fmt(r.confirmedRps),fmt(r.averageMs),fmt(r.p95Ms),fmt(r.errorPercent)])), '',
    `Late recovery vs own baseline: average ${fmt(recoveryAssessment.lateAverageChangePercent)}%; p95 ${fmt(recoveryAssessment.lateP95ChangePercent)}%; throughput ${fmt(recoveryAssessment.lateThroughputChangePercent)}%.`, '',
    table('| Phase | First starts/s | Second starts/s | First avg ms | Second avg ms | First p95 ms | Second p95 ms | First error % | Second error % |',comparisons.map(c=>[c.phase,fmt(c.first.startedRps),fmt(c.second.startedRps),fmt(c.first.averageMs),fmt(c.second.averageMs),fmt(c.first.p95Ms),fmt(c.second.p95Ms),fmt(c.first.errorPercent),fmt(c.second.errorPercent)])), '',
    table('| Resource | First avg | Second avg | First max | Second max |',fields.map(f=>[f,fmt(previous.resourceSummary[f]?.average),fmt(resourceSummary[f]?.average),fmt(previous.resourceSummary[f]?.max),fmt(resourceSummary[f]?.max)])), '',
    `Second-run samples ${resourceSummary.sampleCount}; minimum free RAM ${fmt(resourceSummary.systemAvailableMemoryMiB?.min)} MiB vs first-run ${fmt(previous.resourceSummary.systemAvailableMemoryMiB.min)} MiB.`, '',
    table('| DB phase | Total | Service | Run | Distinct run | Duplicate groups |',db.snapshots.map(s=>[s.phase,s.total,s.service,s.run,s.distinctRun,s.duplicateKeys])), '',
    `Load-added ${dbVerification.loadAdded}; including authentication ${dbVerification.includingAuthAdded}; client-confirmed ${totals.confirmed}; interrupted ${totals.interrupted}; persisted without confirmation ${dbVerification.persistedWithoutClientConfirmation}; DB duplicates ${dbVerification.duplicates}.`, '',
    `Server postcheck: authenticatedGetOk=${post.authenticatedGetOk}, listening=${post.listening}, activeK6Count=${post.activeK6Count}.`, '',
    ...notes.map(n=>'- '+n), '',
    `Artifacts: load-tests/issue21-spike2-dashboard.html, issue21-spike2-analysis.json, issue21-spike2-timeline.json, and results-spike2-${runId}.* (raw reports, resources, DB/phase counts, precheck/execution/health/postcheck, logs).`, '',
];
write('load-tests/issue21-spike2-analysis.json',JSON.stringify(report,null,2)+'\n');
write('load-tests/issue21-spike2-analysis.md',body.join('\n'));
write('load-tests/issue21-spike2-timeline.json',JSON.stringify({runId,plan:data.spikePlan,timeline},null,2)+'\n');
const raw = read(`${stem}.md`);
if (!raw.startsWith('> Interpretation')) write(`${stem}.md`,`> Interpretation: status ${status}; response rates exclude ${totals.interrupted} interrupted attempts. HTTP/network errors are reported separately. See issue21-spike2-analysis.md for DB reconciliation and first/second-run comparison.\n\n`+raw);
const artifacts = [htmlPath,'load-tests/issue21-spike2-analysis.md','load-tests/issue21-spike2-analysis.json','load-tests/issue21-spike2-timeline.json',...['md','json','precheck.json','resources.csv','resources.json','db.json','db-phases.json','execution.json','health.json','postcheck.json','stdout.log','stderr.log'].map(ext=>`${stem}.${ext}`)];
for(const path of artifacts){assert(fs.existsSync(path),`Missing ${path}`);if(path.endsWith('.json'))json(path);if(!path.endsWith('.stderr.log'))assert(fs.statSync(path).size>0,`Empty ${path}`);}
console.log(JSON.stringify({status,totals,phases,recovery,recoveryAssessment,resourceSummary,dbVerification,htmlJsonMatch:true,artifactCount:artifacts.length},null,2));
