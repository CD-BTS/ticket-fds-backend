$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:K6_WEB_DASHBOARD = 'true'
$env:K6_WEB_DASHBOARD_OPEN = 'false'
$env:K6_WEB_DASHBOARD_PORT = '-1'
$env:K6_WEB_DASHBOARD_PERIOD = '5s'
$env:K6_WEB_DASHBOARD_EXPORT = 'load-tests/issue21-stress-1000-dashboard.html'
if (Test-Path $env:K6_WEB_DASHBOARD_EXPORT) { throw 'Dashboard report already exists; refusing to overwrite' }
if (Get-Process k6 -ErrorAction SilentlyContinue) { throw 'Another k6 process is active; refusing concurrent load' }
$serverPid = (Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -First 1).OwningProcess
$logicalProcessors = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
# Read only the existing helper definition, never the server restart/setup code.
$runtimeHelper = Get-Content -Raw .env.issue21-run.ps1
$helperEnd = $runtimeHelper.IndexOf('$ownerId =')
if ($helperEnd -lt 0) { throw 'Cannot identify read-only runtime helper' }
& ([scriptblock]::Create($runtimeHelper.Substring(0,$helperEnd)))
$runtimeEntries = [ProcessEnvironment21]::ReadEnvironment($serverPid).Split([char]0)
$runtimeDb = @($runtimeEntries | Where-Object { $_.StartsWith('DB_URL=') })[0]
if ($runtimeDb -notmatch '^DB_URL=jdbc:mysql://(localhost|127\.0\.0\.1):3307/fairticket_db(?:\?|$)') { throw 'Runtime server database does not match local test database' }
$dbUserEntry=@($runtimeEntries | Where-Object { $_.StartsWith('DB_USERNAME=') })[0]
$dbPasswordEntry=@($runtimeEntries | Where-Object { $_.StartsWith('DB_PASSWORD=') })[0]
if (-not $dbUserEntry -or -not $dbPasswordEntry) { throw 'Existing runtime DB credentials are unavailable' }
function Escape-MySqlOption([string]$value) { return $value.Replace('\','\\').Replace('"','\"').Replace("`n",'\n').Replace("`r",'\r') }
$script:DbClientOptions="[client]`nuser=`"$(Escape-MySqlOption $dbUserEntry.Substring(12))`"`npassword=`"$(Escape-MySqlOption $dbPasswordEntry.Substring(12))`"`n"
$dbUserEntry=$null; $dbPasswordEntry=$null
$runtimeEntries=$null; $runtimeDb=$null
$serverInfo=Get-CimInstance Win32_Process -Filter "ProcessId=$serverPid"
if ($serverInfo.Name -ne 'java.exe') { throw 'Port 8080 is not owned by Java' }
$script:DbSnapshots=[Collections.Generic.List[object]]::new()
$script:HealthChecks=[Collections.Generic.List[object]]::new()
function Start-HealthCheck([string]$phase) {
    $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, "$env:API_BASE_URL/api/v1/users/$($env:USER_IDS.Split(',')[0])")
    $null=$request.Headers.TryAddWithoutValidation('X-API-Key',$env:API_KEY)
    return @{phase=$phase;request=$request;started=[DateTime]::UtcNow;task=$script:HealthClient.SendAsync($request)}
}
function Complete-HealthCheck($pending) {
    $status=$null; $ok=$false; $errorText=$null
    try {
        $response=$pending.task.GetAwaiter().GetResult()
        try {
            $status=[int]$response.StatusCode
            $body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
            $ok=$status -eq 200 -and $body.success -eq $true -and $body.data.userId -eq $env:USER_IDS.Split(',')[0] -and $body.data.serviceId -eq $env:SERVICE_ID
        } finally { $response.Dispose() }
    } catch { $errorText=$_.Exception.GetBaseException().Message }
    finally { $pending.request.Dispose() }
    $result=[pscustomobject]@{phase=$pending.phase;startedUtc=$pending.started.ToString('o');elapsedMs=([DateTime]::UtcNow-$pending.started).TotalMilliseconds;httpStatus=$status;ok=$ok;error=$errorText}
    $script:HealthChecks.Add($result)
    @{runId=$env:RUN_ID;checks=@($script:HealthChecks.ToArray())} | ConvertTo-Json -Depth 5 | Set-Content "load-tests/results-stress-1000-$env:RUN_ID.health.json" -Encoding UTF8
    Write-Host "Server health $($pending.phase): HTTP=$status ok=$ok"
    return $result
}
function Stop-LoadSafely($load,[string]$reason,[string]$stem) {
    @{utc=[DateTime]::UtcNow.ToString('o');reason=$reason;action='Stop k6 only; do not restart or retry';stoppedEarly=$true} | ConvertTo-Json | Set-Content "$stem.abort.json" -Encoding UTF8
    Write-Output "Safety stop: $reason"
    try {
        $null=Invoke-RestMethod -Method Patch -Uri http://127.0.0.1:6565/v1/status -ContentType 'application/json' -Body '{"data":{"type":"status","id":"default","attributes":{"stopped":true}}}' -TimeoutSec 2
    } catch { Write-Output 'k6 stop API unavailable; preserving partial results and stopping only the load process' }
    if (-not $load.WaitForExit(5000)) { Stop-Process -Id $load.Id; $load.WaitForExit() }
}
function Save-DbSnapshot([string]$phase) {
    if ($env:RUN_ID -notmatch '^[0-9a-f-]{36}$') { throw 'Invalid run identifier' }
    $prefix="k6-$env:RUN_ID-"
    $sql="SELECT (SELECT COUNT(*) FROM behavior_event), (SELECT COUNT(*) FROM behavior_event WHERE service_id='load-test-issue18'), (SELECT COUNT(*) FROM behavior_event WHERE service_id='load-test-issue18' AND external_event_id LIKE '$prefix%'), (SELECT COUNT(DISTINCT external_event_id) FROM behavior_event WHERE service_id='load-test-issue18' AND external_event_id LIKE '$prefix%'), (SELECT COUNT(*) FROM (SELECT service_id,external_event_id FROM behavior_event GROUP BY service_id,external_event_id HAVING COUNT(*)>1) AS duplicates);"
    $output = $script:DbClientOptions | docker exec -i fairticket-mysql mysql --defaults-extra-file=/dev/stdin --host=127.0.0.1 --batch --skip-column-names fairticket_db --execute $sql
    if ($LASTEXITCODE -ne 0) { throw 'Read-only DB snapshot failed' }
    $fields=($output -join '').Split("`t")
    if ($fields.Count -ne 5) { throw 'Unexpected DB snapshot format' }
    $snapshot=[pscustomobject]@{phase=$phase;utc=[DateTime]::UtcNow.ToString('o');total=[long]$fields[0];service=[long]$fields[1];run=[long]$fields[2];distinctRun=[long]$fields[3];duplicateKeys=[long]$fields[4]}
    $script:DbSnapshots.Add($snapshot)
    @{runId=$env:RUN_ID; snapshots=@($script:DbSnapshots.ToArray())} | ConvertTo-Json -Depth 5 | Set-Content "load-tests/results-stress-1000-$env:RUN_ID.db.json" -Encoding UTF8
    Write-Output "DB $phase : total=$($snapshot.total), service=$($snapshot.service), run=$($snapshot.run), duplicateKeys=$($snapshot.duplicateKeys)"
    if ($phase -eq 'before_auth' -and $snapshot.run -ne 0) { throw 'Run ID already exists in DB' }
}
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Issue21ExitCode1000 {
    [DllImport("kernel32.dll", SetLastError=true)]
    public static extern bool GetExitCodeProcess(IntPtr handle, out uint exitCode);
}
'@
function Invoke-Issue21MeasuredK6 {
    $stem = "load-tests/results-stress-1000-$env:RUN_ID"
    $script:HealthClient=[Net.Http.HttpClient]::new()
    $script:HealthClient.Timeout=[TimeSpan]::FromSeconds(10)
    $beforeHealth=Complete-HealthCheck (Start-HealthCheck 'before_load')
    if (-not $beforeHealth.ok) { $script:HealthClient.Dispose(); throw 'Server preflight GET failed; no load started' }
    $baselineOs=Get-CimInstance Win32_OperatingSystem
    if ($baselineOs.FreePhysicalMemory -lt 65536) { throw 'Less than 64 MiB RAM available; no load started' }
    Save-DbSnapshot 'before_load'
    $samples = [Collections.Generic.List[object]]::new()
    $previous = Get-Process -Id $serverPid
    $previousCpu = $previous.TotalProcessorTime.TotalSeconds
    $previousTime = [DateTime]::UtcNow
    $started = $previousTime
    if (Get-NetTCPConnection -LocalPort 6565 -State Listen -ErrorAction SilentlyContinue) { throw 'k6 safety API port is already in use' }
    $load = Start-Process -FilePath (Get-Command k6).Source -ArgumentList @('run','--address','127.0.0.1:6565','--quiet','./load-tests/event-stress-test.js') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$stem.stdout.log" -RedirectStandardError "$stem.stderr.log"
    $loadHandle = $load.Handle
    @{computerName=$env:COMPUTERNAME;serverPid=$serverPid;serverProcessName=$serverInfo.Name;k6Pid=$load.Id;k6ProcessName=$load.ProcessName;sameMachine=$true;apiBaseUrl=$env:API_BASE_URL;logicalProcessors=$logicalProcessors;serverStartUtc=$serverInfo.CreationDate.ToUniversalTime().ToString('o');k6StartUtc=$load.StartTime.ToUniversalTime().ToString('o');dashboardPort=-1;dashboardPeriod='5s';vus=1000;durationSeconds=30} | ConvertTo-Json | Set-Content "$stem.execution.json" -Encoding UTF8
    $previousK6Cpu = 0
    $pendingHealth=$null; $nextHealthSeconds=5; $stopReason=$null
    Write-Output "Dashboard: HTML export (HTTP disabled to prevent shutdown waiting) | server PID=$serverPid | k6 PID=$($load.Id)"
    do {
        Start-Sleep -Seconds 1
        $now = [DateTime]::UtcNow
        $serverProcess = Get-Process -Id $serverPid -ErrorAction SilentlyContinue
        if (-not $serverProcess) { $stopReason='Original Java process exited'; Stop-LoadSafely $load $stopReason $stem; break }
        $cpu = $serverProcess.TotalProcessorTime.TotalSeconds
        $elapsed = ($now - $previousTime).TotalSeconds
        $k6Cpu = $null; $k6Memory = $null
        try { $load.Refresh(); $k6Cpu=$load.TotalProcessorTime.TotalSeconds; $k6Memory=$load.WorkingSet64/1MB } catch { }
        $os = Get-CimInstance Win32_OperatingSystem
        $systemCpu = (Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'").PercentProcessorTime
        $samples.Add([pscustomobject]@{
            utc = $now.ToString('o'); elapsedSeconds = ($now-$started).TotalSeconds
            serverCpuPercent = 100 * ($cpu-$previousCpu) / $elapsed / $logicalProcessors
            serverWorkingSetMiB = $serverProcess.WorkingSet64 / 1MB
            serverPrivateMiB = $serverProcess.PrivateMemorySize64 / 1MB
            systemCpuPercent = $systemCpu
            systemUsedMemoryMiB = ($os.TotalVisibleMemorySize-$os.FreePhysicalMemory) / 1024
            k6CpuPercent = if ($null -ne $k6Cpu) { 100*($k6Cpu-$previousK6Cpu)/$elapsed/$logicalProcessors } else { $null }
            k6WorkingSetMiB = $k6Memory
        })
        $previousCpu = $cpu; $previousTime = $now
        if ($null -ne $k6Cpu) { $previousK6Cpu=$k6Cpu }
        $samples[$samples.Count-1] | Export-Csv "$stem.resources.csv" -Append -NoTypeInformation -Encoding UTF8
        $load.Refresh()
        if ($load.HasExited) { break }
        if ($os.FreePhysicalMemory -lt 65536) { $stopReason='Available physical RAM below 64 MiB' }
        if ($pendingHealth -and $pendingHealth.task.IsCompleted) {
            $health=Complete-HealthCheck $pendingHealth
            $pendingHealth=$null
            if (-not $health.ok) { $stopReason='Authenticated server health GET failed or timed out (10 seconds); no retry' }
        }
        if (($now-$started).TotalSeconds -gt 60) { $stopReason='k6 exceeded 60-second safety deadline' }
        if ($stopReason) { Stop-LoadSafely $load $stopReason $stem; break }
        if (-not $pendingHealth -and ($now-$started).TotalSeconds -ge $nextHealthSeconds -and ($now-$started).TotalSeconds -lt 30) {
            $pendingHealth=Start-HealthCheck 'during_load'
            $nextHealthSeconds=($now-$started).TotalSeconds+5
        }
    } while (-not $load.HasExited)
    $load.WaitForExit()
    [uint32]$exitCode = 259
    if (-not [Issue21ExitCode1000]::GetExitCodeProcess($loadHandle, [ref]$exitCode) -or $exitCode -eq 259) { throw 'Cannot confirm k6 exit code; preserved resource CSV and logs' }
    $samples | Export-Csv "$stem.resources.csv" -NoTypeInformation -Encoding UTF8
    @{ serverPid=$serverPid; logicalProcessors=$logicalProcessors; intervalSeconds=1; samples=@($samples.ToArray()); k6ExitCode=$exitCode } | ConvertTo-Json -Depth 5 | Set-Content "$stem.resources.json" -Encoding UTF8
    Get-Content "$stem.stdout.log"
    Get-Content "$stem.stderr.log" -Tail 5
    if ($pendingHealth) { $null=Complete-HealthCheck $pendingHealth }
    $postHealth=Complete-HealthCheck (Start-HealthCheck 'after_load')
    $script:HealthClient.Dispose()
    $postServer=Get-CimInstance Win32_Process -Filter "ProcessId=$serverPid"
    @{utc=[DateTime]::UtcNow.ToString('o');serverPid=$serverPid;processName=$postServer.Name;creationUtc=if ($postServer) {$postServer.CreationDate.ToUniversalTime().ToString('o')} else {$null};listening=[bool](Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Where-Object OwningProcess -eq $serverPid);authenticatedGetOk=$postHealth.ok;activeK6Count=@(Get-Process k6 -ErrorAction SilentlyContinue).Count;stoppedEarly=[bool]$stopReason} | ConvertTo-Json | Set-Content "$stem.postcheck.json" -Encoding UTF8
    $global:LASTEXITCODE = [int]$exitCode
    Save-DbSnapshot 'after_load'
    $global:LASTEXITCODE = [int]$exitCode
}
# Reuse the existing DPAPI authentication, identities and authenticated preflight.
$existing = Get-Content -Raw .env.issue21-test.ps1
if (-not $existing.Contains("`$env:STRESS_VUS='200'")) { throw 'Unexpected existing runner VU configuration' }
if (-not $existing.Contains('k6 run --quiet ./load-tests/event-stress-test.js')) { throw 'Unexpected existing k6 invocation' }
if (-not $existing.Contains("`$env:DURATION_SECONDS='30'")) { throw 'Expected 30-second workload' }
$existing = $existing.Replace("`$env:STRESS_VUS='200'", "`$env:STRESS_VUS='1000'").Replace('Starting 200 VU x 30 seconds', 'Starting 1000 VU x 30 seconds').Replace('k6 run --quiet ./load-tests/event-stress-test.js', 'Invoke-Issue21MeasuredK6')
$existing = $existing.Replace("`$env:RUN_ID=[guid]::NewGuid().ToString()", "`$env:RUN_ID=[guid]::NewGuid().ToString()`r`n Save-DbSnapshot 'before_auth'")
try { & ([scriptblock]::Create($existing)) } finally { $script:DbClientOptions=$null }



