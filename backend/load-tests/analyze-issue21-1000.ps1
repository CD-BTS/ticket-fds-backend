$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$runId='48d87476-3e28-4d24-b35d-ac35f071b4ab'
$stem="load-tests/results-stress-1000-$runId"
$j=Get-Content -Raw "$stem.json" | ConvertFrom-Json
$comparison=Get-Content -Raw load-tests/issue21-stress-comparison.json | ConvertFrom-Json
$current=@($comparison.runs | Where-Object vus -eq 1000)[0]
$db=Get-Content -Raw "$stem.db.json" | ConvertFrom-Json
$health=Get-Content -Raw "$stem.health.json" | ConvertFrom-Json
$post=Get-Content -Raw "$stem.postcheck.json" | ConvertFrom-Json
$execution=Get-Content -Raw "$stem.execution.json" | ConvertFrom-Json
$abort=Get-Content -Raw "$stem.abort.json" | ConvertFrom-Json
$before=@($db.snapshots | Where-Object phase -eq before_load)[0]
$beforeAuth=@($db.snapshots | Where-Object phase -eq before_auth)[0]
$after=@($db.snapshots | Where-Object phase -eq after_load)[0]
$incomplete=$current.attempts-$current.completedRequests
$unconfirmedPersisted=$after.run-$current.created
$warnings=@(Get-Content "$stem.stderr.log" | Where-Object {$_ -match 'level=warning' -and $_ -match 'Request Failed'})
$refused=@($warnings | Where-Object {$_ -match 'actively refused'}).Count
if ($refused -ne $current.networkFailures -or $current.httpFailures -ne 0) { throw 'Unexpected failure mix; inspect logs' }
if ($after.run -ne $after.distinctRun -or $after.duplicateKeys -ne 0 -or $before.run -ne 0) { throw 'Duplicate or run identity validation failed' }
if ($after.total-$before.total -ne $after.run -or $after.service-$before.service -ne $after.run) { throw 'DB load delta mismatch' }
if ($incomplete -ne $unconfirmedPersisted -or $after.run+$current.networkFailures -ne $current.attempts) { throw 'Interrupted-request reconciliation mismatch' }
$notes=@(
    'One load execution only. Planned 1000 VU x 30s; stopped early after 13.422856 seconds of k6 metric time because available physical RAM fell below 64 MiB. No retry, restart, server tuning or Spike Test.',
    'Same 500 VU authentication, 100 user IDs, event workload, 10s request timeout, dashboard=true/open=false/port=-1/period=5s and resource collection method. Added a loopback k6 REST stop API and occasional authenticated read-only GET health probes (10s timeout). These safety probes add a small amount of server work outside k6 request metrics.',
    'k6 exit code 103 and log test run stopped from REST API confirm the safety stop. The fallback force-stop was not needed. CPU 100% alone was not a stop condition; the available RAM threshold triggered.',
    'Completed response metrics: 3214 responses = 2496 client-confirmed successes + 718 network errors. 999 of 4213 started attempts were interrupted and excluded from success/error rate denominators and completed-request RPS. No HTTP error responses or response validation failures were recorded.',
    'All 718 Request Failed warning lines report connection refusal. The script logged detailed EVENT FAILURE lines only on each VU first iteration (706 lines, error code 1212), so those detail lines are not a complete failure counter. Warnings and detail lines must not be added together.',
    'DB contains 3495 unique run events, 999 more than client-confirmed successes. This exactly matches interrupted attempts; server processing can persist requests after k6 stops waiting for responses. 3495 DB rows + 718 refused connections account for all 4213 started attempts. This is not evidence of duplicate or missing records.',
    'The raw k6 Markdown count/30 figures (140.43 attempted/s, 83.20 client-confirmed created/s) are not measured throughput because the run was aborted. Use completed-request RPS 239.44 over actual elapsed time for this partial observation; do not compare it as a completed 30s capacity test.',
    'Same PC: Java PID 6324 and k6 PID 32572, 8 logical CPUs, API localhost:8080. Whole-system CPU and memory pressure include k6, Java, MySQL/Docker, monitoring and other programs. The observed limit includes shared-host resource competition and cannot isolate server-only capacity.',
    'Resource averages are unweighted averages of 5 samples through the safety stop, including startup. One-second sleep plus CIM overhead caused irregular intervals. Memory denotes resident/private committed memory, not Java heap. CPU is normalized to all 8 logical processors.',
    'Spring Boot retained its original PID/start time and was listening after the stop. Authenticated GET returned HTTP 200 with matching service/user, taking 6052.40 ms; reachability recovered but low-latency recovery is not established by this one check.',
    'Existing DB records were not updated or deleted. Load added 3495 rows, authenticated POST preflight added one more. Health GET requests did not create records. HTML and JSON counters/rates/latencies match within dashboard rounding; dashboard RPS uses a separate reporting interval.'
)
$report=[ordered]@{
    runId=$runId;status='ABORTED';plannedSeconds=30;actualMetricSeconds=$j.state.testRunDurationMs/1000;abort=$abort
    result=$current;incompleteAttempts=$incomplete;persistedWithoutClientConfirmation=$unconfirmedPersisted
    networkErrorMessages=@(@{type='connection_refused';status=0;observedDetailErrorCode=1212;count=$refused;message='No connection could be made because the target machine actively refused it.'})
    db=[ordered]@{snapshots=$db.snapshots;loadAdded=$after.total-$before.total;addedIncludingAuth=$after.total-$beforeAuth.total;runEvents=$after.run;distinctRunEvents=$after.distinctRun;duplicateKeyGroups=$after.duplicateKeys;attemptsReconciled=$true}
    health=$health;postcheck=$post;execution=$execution;notes=$notes
}
$report | ConvertTo-Json -Depth 14 | Set-Content load-tests/issue21-stress-1000-analysis.json -Encoding UTF8
$lines=@('# Issue #21: 1000 VU partial test - safety stop','','**ABORTED: not a completed 30-second test.** Available physical RAM fell below 64 MiB; k6 stopped through its REST API (exit 103). No retry or Spike Test.', '',"RUN_ID: $runId",'', '| Metric | Partial result |','|---|---:|', ('| Actual k6 metric duration (seconds) | {0:N6} |' -f ($j.state.testRunDurationMs/1000)))
foreach($metric in @('attempts','completedRequests','created','actualRps','averageMs','p95Ms','successPercent','errorPercent','networkFailures','httpFailures')) { $lines+=('| {0} | {1:N2} |' -f $metric,$current.$metric) }
$lines+=@('', 'Interrupted attempts: 999. Success/error percentages use 3214 completed responses, excluding interrupted attempts. Network failures: 718 connection refusals; HTTP errors: 0.','', '| Resource | Sample average | Sample maximum |','|---|---:|---:|')
foreach($metric in @('systemCpuPercent','serverCpuPercent','k6CpuPercent','serverWorkingSetMiB','serverPrivateMiB','k6WorkingSetMiB','systemUsedMemoryMiB')) {
    $s=$current.resources.$metric
    $lines+=('| {0} | {1:N2} | {2:N2} |' -f $metric,$s.average,$s.maximum)
}
$lines+=@('', '| DB phase | Total events | Service events | Run events | Distinct run events | Duplicate groups |','|---|---:|---:|---:|---:|---:|')
foreach($s in $db.snapshots) { $lines+=('| {0} | {1} | {2} | {3} | {4} | {5} |' -f $s.phase,$s.total,$s.service,$s.run,$s.distinctRun,$s.duplicateKeys) }
$lines+=@('', 'DB load delta 3495 matches unique run events. Including preflight: 3496 new rows. DB/client success difference 999 matches interrupted attempts.','', '| VU | Status | k6 elapsed-time RPS | Avg ms | p95 ms | Success % | Error % | Network failures |','|---|---|---:|---:|---:|---:|---:|---:|')
foreach($r in $comparison.runs) {
    $status=if($r.stoppedEarly){'ABORTED (13.42s)'}else{'Completed (30s planned)'}
    $lines+=('| {0} | {1} | {2:N2} | {3:N2} | {4:N2} | {5:N2} | {6:N2} | {7} |' -f $r.vus,$status,$r.actualRps,$r.averageMs,$r.p95Ms,$r.successPercent,$r.errorPercent,$r.networkFailures)
}
$lines+=@('', 'After stop: authenticated server GET HTTP 200 (6052.40 ms), original Java PID 6324 remained listening, no active k6.','')+$notes+@('', 'Safe-stop API reference: https://grafana.com/docs/k6/latest/reference/k6-rest-api/')
$lines | Set-Content load-tests/issue21-stress-1000-analysis.md -Encoding UTF8
$rawMarkdown=Get-Content -Raw "$stem.md"
if (-not $rawMarkdown.StartsWith('> ABORTED')) {
    ("> ABORTED after 13.42s due to available RAM below 64 MiB. Count/planned-30s RPS below is NOT measured throughput. See issue21-stress-1000-analysis.md for completed-response RPS, interrupted requests and DB reconciliation.`r`n`r`n"+$rawMarkdown) | Set-Content "$stem.md" -Encoding UTF8
}
$files=@("$stem.md","$stem.json","$stem.resources.csv","$stem.resources.json","$stem.db.json","$stem.execution.json","$stem.health.json","$stem.postcheck.json","$stem.abort.json","$stem.stdout.log","$stem.stderr.log",'load-tests/issue21-stress-1000-dashboard.html','load-tests/issue21-stress-1000-analysis.md','load-tests/issue21-stress-1000-analysis.json','load-tests/issue21-stress-comparison.md','load-tests/issue21-stress-comparison.json')
foreach($file in $files) {
    $item=Get-Item $file
    if ($item.Length -eq 0) { throw "Empty artifact: $file" }
    if ($file.EndsWith('.json')) { $null=Get-Content -Raw $file | ConvertFrom-Json }
    Write-Output "Verified: $file ($($item.Length) bytes)"
}
