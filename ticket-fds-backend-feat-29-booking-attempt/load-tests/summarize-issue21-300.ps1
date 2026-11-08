$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$runId = 'd7670ad9-e74a-4323-aa99-fded01c09d19'
$stem = "load-tests/results-stress-300-$runId"
$baseline = Get-Content -Raw load-tests/results-stress-200-b6536e3d-f6a4-4a3f-9f49-da299e3a732f.json | ConvertFrom-Json
$current = Get-Content -Raw "$stem.json" | ConvertFrom-Json
$resources = Get-Content -Raw "$stem.resources.json" | ConvertFrom-Json
function Metrics($data, $vu) {
    $groupName = "vus_$vu"
    $attempts = $data.metrics."event_attempts{test_group:$groupName}".values.count
    $network = $data.metrics.event_network_failure_count.values.count
    if ($null -eq $network) { $network = 0 }
    [pscustomobject]@{
        vus=$vu; seconds=30; attempts=$attempts
        created=$data.metrics."events_created{test_group:$groupName}".values.count
        plannedRps=$attempts/30
        actualRps=$data.metrics.http_reqs.values.rate
        averageMs=$data.metrics."http_req_duration{test_group:$groupName}".values.avg
        p95Ms=$data.metrics."http_req_duration{test_group:$groupName}".values.'p(95)'
        successPercent=100*$data.metrics."event_success_rate{test_group:$groupName}".values.rate
        errorPercent=100*$data.metrics."event_error_rate{test_group:$groupName}".values.rate
        httpFailedPercent=100*$data.metrics."http_req_failed{test_group:$groupName}".values.rate
        networkFailures=$network
    }
}
$before = Metrics $baseline 200
$after = Metrics $current 300
$active = @($resources.samples | Where-Object { $_.elapsedSeconds -le 30 })
$stats = [ordered]@{sampleCount=$active.Count; window='First 30 seconds after launching k6; approximately the active phase, including startup'; serverPid=$resources.serverPid; logicalProcessors=$resources.logicalProcessors}
foreach ($metric in @('serverCpuPercent','serverWorkingSetMiB','serverPrivateMiB','systemCpuPercent','systemUsedMemoryMiB')) {
    $s = $active | Measure-Object -Property $metric -Average -Maximum
    $stats[$metric] = @{ average=$s.Average; maximum=$s.Maximum }
}
$comparison = [ordered]@{
    runId=$runId; baselineRunId=$baseline.runId; baseline=$before; current=$after
    rpsChangePercent=100*($after.plannedRps/$before.plannedRps-1)
    averageChangePercent=100*($after.averageMs/$before.averageMs-1)
    p95ChangePercent=100*($after.p95Ms/$before.p95Ms-1)
    resourceSummary=$stats; k6ExitCode=$resources.k6ExitCode
}
$comparison | ConvertTo-Json -Depth 8 | Set-Content load-tests/issue21-stress-300-analysis.json -Encoding UTF8
$lines = @('# Issue #21: 300 VU x 30 seconds', '', "RUN_ID: $runId", "Baseline RUN_ID: $($baseline.runId)", '', '| Metric | 200 VU | 300 VU |', '|---|---:|---:|')
foreach ($metric in @('attempts','created','plannedRps','actualRps','averageMs','p95Ms','successPercent','errorPercent','httpFailedPercent','networkFailures')) {
    $lines += ('| {0} | {1:N2} | {2:N2} |' -f $metric,$before.$metric,$after.$metric)
}
$lines += @('', ('RPS change: {0:N2}%; average latency change: {1:N2}%; p95 change: {2:N2}%.' -f $comparison.rpsChangePercent,$comparison.averageChangePercent,$comparison.p95ChangePercent), '', 'Planned RPS = attempts / 30s, including graceful completions, matching the baseline report. Actual RPS uses the k6 elapsed-time metric.', '', 'Resource samples: first 30 seconds after launching k6, approximately the active phase including startup. Browser dashboard shutdown waiting is excluded. CPU is normalized to the whole machine (8 logical processors). Working set is resident memory; private memory is committed memory.', '', '| Resource | Average | Maximum |', '|---|---:|---:|')
foreach ($metric in @('serverCpuPercent','serverWorkingSetMiB','serverPrivateMiB','systemCpuPercent','systemUsedMemoryMiB')) {
    $lines += ('| {0} | {1:N2} | {2:N2} |' -f $metric,$stats[$metric].average,$stats[$metric].maximum)
}
$lines += @('', 'No server tuning or database cleanup was performed. The existing POST workload and authenticated preflight create new test events; existing records were not updated or deleted.', '', 'Dashboard during execution: http://localhost:5665', 'HTML: load-tests/issue21-stress-300-dashboard.html')
$lines | Set-Content load-tests/issue21-stress-300-analysis.md -Encoding UTF8
$comparison | ConvertTo-Json -Depth 8
foreach ($file in @("$stem.md", "$stem.json", "$stem.resources.json", "$stem.resources.csv", 'load-tests/issue21-stress-300-dashboard.html', 'load-tests/issue21-stress-300-analysis.md', 'load-tests/issue21-stress-300-analysis.json')) {
    $item=Get-Item $file
    if ($item.Length -eq 0) { throw "Empty report: $file" }
    if ($file.EndsWith('.json')) { $null = Get-Content -Raw $file | ConvertFrom-Json }
    Write-Output "Verified: $file ($($item.Length) bytes)"
}
