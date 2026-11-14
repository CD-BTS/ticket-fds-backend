$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$runId='4f8fbf55-c380-412f-b2cc-f0c0325e1120'
$stem="load-tests/results-stress-500-$runId"
$summary=Get-Content -Raw "$stem.json" | ConvertFrom-Json
$comparison=Get-Content -Raw load-tests/issue21-stress-comparison.json | ConvertFrom-Json
$current=@($comparison.runs | Where-Object vus -eq 500)[0]
$previous=@($comparison.runs | Where-Object vus -eq 400)[0]
$db=Get-Content -Raw "$stem.db.json" | ConvertFrom-Json
$execution=Get-Content -Raw "$stem.execution.json" | ConvertFrom-Json
$postcheck=Get-Content -Raw "$stem.postcheck.json" | ConvertFrom-Json
$beforeAuth=@($db.snapshots | Where-Object phase -eq before_auth)[0]
$before=@($db.snapshots | Where-Object phase -eq before_load)[0]
$after=@($db.snapshots | Where-Object phase -eq after_load)[0]
if ($before.run -ne 0 -or $after.run -ne $current.created -or $after.run -ne $after.distinctRun -or $after.duplicateKeys -ne 0) { throw 'DB run-count or duplicate verification failed' }
if ($after.total-$before.total -ne $current.created -or $after.service-$before.service -ne $current.created) { throw 'DB count delta does not match API successes' }
$failureDetails=@(Get-Content "$stem.stderr.log" | ForEach-Object {
    if ($_ -match 'time="([^"]+)".*\[EVENT FAILURE\] type=([^,]+), status=([^,]+), error=(.*), error_code=([^" ]+)') {
        [pscustomobject]@{utcOrOffsetTime=$matches[1];type=$matches[2];status=[int]$matches[3];message=$matches[4];code=[int]$matches[5]}
    }
})
$failureGroups=@($failureDetails | Group-Object utcOrOffsetTime,type,status,message,code | ForEach-Object {
    $f=$_.Group[0]
    [pscustomobject]@{time=$f.utcOrOffsetTime;type=$f.type;status=$f.status;message=$f.message;code=$f.code;loggedCount=$_.Count}
})
$totalFailures=$summary.metrics.event_failure_count.values.count
if ($failureDetails.Count -ne $current.networkFailures) { throw 'Network log count differs from counter; inspect manually' }
$dbVerification=[ordered]@{
    beforeAuth=$beforeAuth;beforeLoad=$before;afterLoad=$after
    totalAddedDuringLoad=$after.total-$before.total
    serviceAddedDuringLoad=$after.service-$before.service
    totalAddedIncludingAuth=$after.total-$beforeAuth.total
    authenticationProbeRows=$before.total-$beforeAuth.total
    runCountMatchesCreated=$true;duplicateKeyGroups=$after.duplicateKeys
}
$change=[ordered]@{
    comparedToVus=400
    rpsPercent=100*($current.plannedRps/$previous.plannedRps-1)
    createdRpsPercent=100*($current.createdRps/$previous.createdRps-1)
    averageLatencyPercent=100*($current.averageMs/$previous.averageMs-1)
    p95Percent=100*($current.p95Ms/$previous.p95Ms-1)
}
$notes=@(
    'Only one 500 VU x 30 second load run was executed. Initial root DB authentication failed before preflight/load; the existing runtime DB credentials were then used via stdin without changing credentials or saving secrets.',
    'Same 400 VU workload, dashboard=true, open=false, port=-1, period=5s, 100 users and one-second sleep plus CIM resource collection. Extra DB queries were executed outside the load interval; k6 CPU/memory sampling was added.',
    'All 262 logged failures occurred at 20:10:37 +09:00 (startup). They match the network failure counter. HTTP status=0 and error_code=1212 with dial: connection refused; no HTTP error responses or validation failures were recorded. Request Failed warnings and EVENT FAILURE lines describe the same requests, not separate failures.',
    'Connection refusal is observed evidence; accept-queue overflow, server scheduling or other OS causes cannot be distinguished by this test. The Java PID/start time remained unchanged and port 8080 was listening after the test.',
    'k6 and Spring Boot ran as local processes on the same Windows PC using localhost:8080 and 8 logical CPUs. They compete with each other, MySQL/Docker and other processes for CPU and memory; resource sampling also adds overhead. High system CPU does not establish Java-only CPU saturation.',
    '500 VU delivered higher throughput and lower latency than the 400 VU single run but failed the success>=99% and error<=1% criteria. The non-monotonic throughput and lower whole-system CPU average argue against treating these single runs as a reliable server capacity curve. Fast failed connections are included in aggregate latency and total-request RPS; successful created/s is reported separately.',
    'Resource statistics are unweighted sample averages/maxima in the first 30 seconds after launching k6, including startup. Sampling intervals exceed one second because CIM collection takes time. CPU is normalized to all 8 logical CPUs; resident and private committed memory are not Java heap measurements.',
    'No existing DB records were updated/deleted; no server performance settings changed. New events were created by the authenticated preflight and load workload. No 1000 VU test was run.'
)
$report=[ordered]@{runId=$runId;result=$current;deltaFrom400=$change;totalFailures=$totalFailures;httpErrors=$current.httpFailures;networkErrors=$current.networkFailures;responseValidationFailures=$totalFailures-$current.httpFailures-$current.networkFailures;loggedErrorGroups=$failureGroups;dbVerification=$dbVerification;execution=$execution;postcheck=$postcheck;notes=$notes}
$report | ConvertTo-Json -Depth 12 | Set-Content load-tests/issue21-stress-500-analysis.json -Encoding UTF8
$lines=@('# Issue #21: 500 VU x 30 seconds final analysis','',"RUN_ID: $runId",'','Completed; k6 exit code 99 (threshold violation), not a process crash. Spring Boot remained running.','', '| Metric | Value |','|---|---:|')
foreach($metric in @('attempts','created','plannedRps','actualRps','createdRps','averageMs','p95Ms','successPercent','errorPercent','httpFailures','networkFailures')) { $lines+=('| {0} | {1:N2} |' -f $metric,$current.$metric) }
$lines+=@('','Failures: 262 network failures, HTTP status 0, error_code 1212, message `dial: connection refused`. All were logged at startup (20:10:37 +09:00); HTTP errors: 0; response validation failures: 0.','', '| Resource | Sample average | Sample maximum |','|---|---:|---:|')
foreach ($metric in @('systemCpuPercent','serverCpuPercent','serverWorkingSetMiB','serverPrivateMiB','systemUsedMemoryMiB','k6CpuPercent','k6WorkingSetMiB')) {
    $s=$current.resources.$metric
    $lines+=('| {0} | {1:N2} | {2:N2} |' -f $metric,$s.average,$s.maximum)
}
$lines+=@('', '| DB phase | All events | Service events | Run events | Distinct run events | Duplicate key groups |','|---|---:|---:|---:|---:|---:|')
foreach ($s in $db.snapshots) { $lines+=('| {0} | {1} | {2} | {3} | {4} | {5} |' -f $s.phase,$s.total,$s.service,$s.run,$s.distinctRun,$s.duplicateKeys) }
$lines+=@('', 'Load-only DB delta: 9,991, matching successful API responses. Including the authenticated preflight: 9,992. Run has 9,991 distinct external event IDs; duplicate groups across the entire table: 0.','', '| VU | Request RPS (count/30) | Created/s | Avg ms | p95 ms | Success % | Error % | Network failures |','|---|---:|---:|---:|---:|---:|---:|---:|')
foreach ($r in $comparison.runs) { $lines+=('| {0} | {1:N2} | {2:N2} | {3:N2} | {4:N2} | {5:N2} | {6:N2} | {7} |' -f $r.vus,$r.plannedRps,$r.createdRps,$r.averageMs,$r.p95Ms,$r.successPercent,$r.errorPercent,$r.networkFailures) }
$lines+=@('', ('400 -> 500 VU: request RPS {0:N2}%; created/s {1:N2}%; mean latency {2:N2}%; p95 {3:N2}%.' -f $change.rpsPercent,$change.createdRpsPercent,$change.averageLatencyPercent,$change.p95Percent), '', 'Execution evidence: Java PID 6324, k6 PID 30652; same Windows computer; API origin http://localhost:8080. No active k6 after completion.','')+$notes
$lines | Set-Content load-tests/issue21-stress-500-analysis.md -Encoding UTF8
$files=@("$stem.md","$stem.json","$stem.resources.csv","$stem.resources.json","$stem.db.json","$stem.execution.json","$stem.postcheck.json","$stem.stdout.log","$stem.stderr.log",'load-tests/issue21-stress-500-dashboard.html','load-tests/issue21-stress-500-analysis.md','load-tests/issue21-stress-500-analysis.json','load-tests/issue21-stress-comparison.md','load-tests/issue21-stress-comparison.json')
foreach ($file in $files) {
    $item=Get-Item $file
    if ($item.Length -eq 0) { throw "Empty file: $file" }
    if ($file.EndsWith('.json')) { $null=Get-Content -Raw $file | ConvertFrom-Json }
    Write-Output "Verified: $file ($($item.Length) bytes)"
}
$change | ConvertTo-Json
$failureGroups | ConvertTo-Json
