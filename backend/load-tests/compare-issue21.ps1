$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Read-Dashboard($path) {
    $html=Get-Content -Raw $path
    if ($html -notmatch '<html') { throw "Invalid HTML: $path" }
    $encoded=[regex]::Match($html,'(?s)<script id="data"[^>]*>(.*?)</script>').Groups[1].Value.Trim()
    $stream=[IO.MemoryStream]::new([Convert]::FromBase64String($encoded))
    $zip=[IO.Compression.GzipStream]::new($stream,[IO.Compression.CompressionMode]::Decompress)
    $reader=[IO.StreamReader]::new($zip)
    try { $events=@($reader.ReadToEnd() -split "`n" | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }) } finally { $reader.Dispose() }
    $names=@($events | Where-Object event -eq metric | ForEach-Object { $_.data.PSObject.Properties.Name } | Sort-Object -Unique)
    $cumulative=@($events | Where-Object event -eq cumulative)[-1].data
    $metrics=@{}
    for ($i=0;$i -lt $names.Count;$i++) { $metrics[$names[$i]]=$cumulative[$i] }
    return $metrics
}
$runs=@(
    @{vu=200; id='b6536e3d-f6a4-4a3f-9f49-da299e3a732f'},
    @{vu=300; id='d7670ad9-e74a-4323-aa99-fded01c09d19'},
    @{vu=400; id='62ea94d5-f972-41bb-b90b-9e995a879da4'},
    @{vu=500; id='4f8fbf55-c380-412f-b2cc-f0c0325e1120'},
    @{vu=1000; id='48d87476-3e28-4d24-b35d-ac35f071b4ab'}
)
$results=@()
$verified=@()
foreach ($run in $runs) {
    $vu=$run.vu; $stem="load-tests/results-stress-$vu-$($run.id)"
    $j=Get-Content -Raw "$stem.json" | ConvertFrom-Json
    $md=Get-Content -Raw "$stem.md"
    if ($j.groups[0].seconds -ne 30 -or $j.runId -ne $run.id -or $j.userCount -ne 100 -or -not $md.Contains($run.id)) { throw "Unexpected metadata: $stem" }
    $m=$j.metrics
    $aborted=Test-Path "$stem.abort.json"
    $row=[ordered]@{
        vus=$vu; runId=$run.id; attempts=$m.event_attempts.values.count; created=$m.events_created.values.count
        plannedRps=$m.event_attempts.values.count/30; actualRps=$m.http_reqs.values.rate
        averageMs=$m.http_req_duration.values.avg; p95Ms=$m.http_req_duration.values.'p(95)'
        successPercent=100*$m.event_success_rate.values.rate; errorPercent=100*$m.event_error_rate.values.rate
        networkFailures=0; httpFailures=0; createdRps=$m.events_created.values.count/30; thresholdPassed=($md -match 'Threshold: PASS')
        stoppedEarly=$aborted; completedRequests=$m.http_reqs.values.count
        actualSeconds=if ($j.state.testRunDurationMs) {$j.state.testRunDurationMs/1000} else {$m.http_reqs.values.count/$m.http_reqs.values.rate}
    }
    if ($m.event_network_failure_count) { $row.networkFailures=$m.event_network_failure_count.values.count }
    if ($m.event_http_failure_count) { $row.httpFailures=$m.event_http_failure_count.values.count }
    if ($aborted) { $row.plannedRps=$null; $row.createdRps=$null }
    $verified+=@("$stem.md","$stem.json")
    if ($vu -ge 300) {
        $dashboard="load-tests/issue21-stress-$vu-dashboard.html"
        $d=Read-Dashboard $dashboard
        foreach ($metric in @('event_attempts','events_created','event_success_rate','event_error_rate','http_req_failed')) {
            $field=if ($metric -in @('event_attempts','events_created')) {'count'} else {'rate'}
            if ([math]::Abs($d[$metric][0]-$m.$metric.values.$field) -gt 0.0001) { throw "HTML/JSON mismatch: $vu $metric" }
        }
        if ([math]::Abs($d.http_req_duration[0]-$row.averageMs) -gt 0.1 -or [math]::Abs($d.http_req_duration[5]-$row.p95Ms) -gt 0.1) { throw "HTML/JSON latency mismatch: $vu" }
        $row.htmlJsonMatch=$true
        $row.htmlRps=$d.http_reqs[1]
        $r=Get-Content -Raw "$stem.resources.json" | ConvertFrom-Json
        $csv=@(Import-Csv "$stem.resources.csv")
        if ($csv.Count -ne $r.samples.Count -or $csv.Count -eq 0) { throw "Resource CSV/JSON mismatch: $vu" }
        $active=@($r.samples | Where-Object elapsedSeconds -le 30)
        $stats=[ordered]@{samples=$active.Count; firstSampleSeconds=$active[0].elapsedSeconds; lastSampleSeconds=$active[-1].elapsedSeconds; exitCode=$r.k6ExitCode}
        $resourceMetrics=@('serverCpuPercent','serverWorkingSetMiB','serverPrivateMiB','systemCpuPercent','systemUsedMemoryMiB')
        if ($vu -ge 500) { $resourceMetrics+=@('k6CpuPercent','k6WorkingSetMiB') }
        foreach ($metric in $resourceMetrics) {
            $s=$active | Measure-Object $metric -Average -Maximum
            $stats[$metric]=@{average=$s.Average; maximum=$s.Maximum}
        }
        $row.resources=$stats
        $verified+=@($dashboard,"$stem.resources.csv","$stem.resources.json")
    }
    $results += [pscustomobject]$row
}
$report=[ordered]@{runs=$results; verifiedFiles=$verified; notes=@('Planned RPS includes graceful completions divided by 30 seconds; actualRps uses k6 elapsed time. Dashboard RPS has its own reporting interval and can differ.','HTML metrics match JSON within dashboard rounding (0.1 ms latency tolerance).','Resources use samples in the first 30 seconds after process launch, including startup; dashboard shutdown waiting is excluded. One second sleep plus Windows CIM collection overhead means actual intervals exceed one second. CPU percentages are normalized to the whole machine (8 logical CPUs).','200 VU resource measurements are unavailable. 300 VU exit code was not captured; its process exited and all thresholds passed.','No existing DB records were updated/deleted and no server tuning changed; normal test POST requests and authentication probes created test events. Sequential single runs cannot establish a reproducible server limit; system saturation and shared load-generator resources affect comparison.')}
$report | ConvertTo-Json -Depth 10 | Set-Content load-tests/issue21-stress-comparison.json -Encoding UTF8
$lines=@('# Issue #21: 200 / 300 / 400 / 500 / 1000 VU comparison','','Planned workloads: 30 seconds, 100 reused users, existing API authentication. 1000 VU was STOPPED EARLY because available physical RAM fell below 64 MiB; its partial results are not a completed 30-second test.','','| VU | Status | Attempts | Completed requests | RPS (count/30) | k6 RPS | Avg ms | p95 ms | Success % | Error % | Network failures |','|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|')
foreach ($r in $results) {
    $status=if ($r.stoppedEarly) {'ABORTED'} else {'Completed'}
    $planned=if ($r.stoppedEarly) {'N/A'} else {'{0:N2}' -f $r.plannedRps}
    $lines+=('| {0} | {1} | {2} | {3} | {4} | {5:N2} | {6:N2} | {7:N2} | {8:N2} | {9:N2} | {10} |' -f $r.vus,$status,$r.attempts,$r.completedRequests,$planned,$r.actualRps,$r.averageMs,$r.p95Ms,$r.successPercent,$r.errorPercent,$r.networkFailures)
}
$lines+=@('','| VU | Samples | Java CPU avg/max % | Java resident avg/max MiB | Java private avg/max MiB | System CPU avg/max % | System used RAM avg/max MiB |','|---|---:|---:|---:|---:|---:|---:|')
foreach ($r in @($results | Where-Object vus -ge 300)) {
    $s=$r.resources
    $lines+=('| {0} | {1} | {2:N2}/{3:N2} | {4:N2}/{5:N2} | {6:N2}/{7:N2} | {8:N2}/{9:N2} | {10:N2}/{11:N2} |' -f $r.vus,$s.samples,$s.serverCpuPercent.average,$s.serverCpuPercent.maximum,$s.serverWorkingSetMiB.average,$s.serverWorkingSetMiB.maximum,$s.serverPrivateMiB.average,$s.serverPrivateMiB.maximum,$s.systemCpuPercent.average,$s.systemCpuPercent.maximum,$s.systemUsedMemoryMiB.average,$s.systemUsedMemoryMiB.maximum)
}
foreach ($pair in @(@(0,1),@(1,2),@(2,3))) {
    $a=$results[$pair[0]]; $b=$results[$pair[1]]
    $lines+=('', ('{0} -> {1} VU: throughput {2:N2}%; mean latency {3:N2}%; p95 {4:N2}%.' -f $a.vus,$b.vus,(100*($b.plannedRps/$a.plannedRps-1)),(100*($b.averageMs/$a.averageMs-1)),(100*($b.p95Ms/$a.p95Ms-1))))
}
$lines+=@('')+$report.notes+@('','Verified files:')+@($verified | ForEach-Object { '- '+$_ })
$lines+=@('', '1000 VU: 13.42 seconds of partial metrics; success/error rates use 3,214 completed responses. 999 attempts were interrupted and are excluded from those rates. The DB contains 3,495 run events versus 2,496 client-confirmed successes, a difference of 999. Do not use counts divided by planned 30 seconds as measured throughput for this aborted run.')
$lines | Set-Content load-tests/issue21-stress-comparison.md -Encoding UTF8
$report | ConvertTo-Json -Depth 10
