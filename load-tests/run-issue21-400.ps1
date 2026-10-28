$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:K6_WEB_DASHBOARD = 'true'
$env:K6_WEB_DASHBOARD_OPEN = 'false'
$env:K6_WEB_DASHBOARD_PORT = '-1'
$env:K6_WEB_DASHBOARD_PERIOD = '5s'
$env:K6_WEB_DASHBOARD_EXPORT = 'load-tests/issue21-stress-400-dashboard.html'
if (Test-Path $env:K6_WEB_DASHBOARD_EXPORT) { throw 'Dashboard report already exists; refusing to overwrite' }
$serverPid = (Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -First 1).OwningProcess
$logicalProcessors = (Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Issue21ExitCode {
    [DllImport("kernel32.dll", SetLastError=true)]
    public static extern bool GetExitCodeProcess(IntPtr handle, out uint exitCode);
}
'@
function Invoke-Issue21MeasuredK6 {
    $stem = "load-tests/results-stress-400-$env:RUN_ID"
    $samples = [Collections.Generic.List[object]]::new()
    $previous = Get-Process -Id $serverPid
    $previousCpu = $previous.TotalProcessorTime.TotalSeconds
    $previousTime = [DateTime]::UtcNow
    $started = $previousTime
    $load = Start-Process -FilePath (Get-Command k6).Source -ArgumentList @('run','--quiet','./load-tests/event-stress-test.js') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$stem.stdout.log" -RedirectStandardError "$stem.stderr.log"
    $loadHandle = $load.Handle
    Write-Output "Dashboard: HTML export (HTTP disabled to prevent shutdown waiting) | server PID=$serverPid | k6 PID=$($load.Id)"
    do {
        Start-Sleep -Seconds 1
        $now = [DateTime]::UtcNow
        $serverProcess = Get-Process -Id $serverPid
        $cpu = $serverProcess.TotalProcessorTime.TotalSeconds
        $elapsed = ($now - $previousTime).TotalSeconds
        $os = Get-CimInstance Win32_OperatingSystem
        $systemCpu = (Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'").PercentProcessorTime
        $samples.Add([pscustomobject]@{
            utc = $now.ToString('o'); elapsedSeconds = ($now-$started).TotalSeconds
            serverCpuPercent = 100 * ($cpu-$previousCpu) / $elapsed / $logicalProcessors
            serverWorkingSetMiB = $serverProcess.WorkingSet64 / 1MB
            serverPrivateMiB = $serverProcess.PrivateMemorySize64 / 1MB
            systemCpuPercent = $systemCpu
            systemUsedMemoryMiB = ($os.TotalVisibleMemorySize-$os.FreePhysicalMemory) / 1024
        })
        $previousCpu = $cpu; $previousTime = $now
        $samples[$samples.Count-1] | Export-Csv "$stem.resources.csv" -Append -NoTypeInformation -Encoding UTF8
        $load.Refresh()
    } while (-not $load.HasExited)
    $load.WaitForExit()
    [uint32]$exitCode = 259
    if (-not [Issue21ExitCode]::GetExitCodeProcess($loadHandle, [ref]$exitCode) -or $exitCode -eq 259) { throw 'Cannot confirm k6 exit code; preserved resource CSV and logs' }
    $samples | Export-Csv "$stem.resources.csv" -NoTypeInformation -Encoding UTF8
    @{ serverPid=$serverPid; logicalProcessors=$logicalProcessors; intervalSeconds=1; samples=@($samples.ToArray()); k6ExitCode=$exitCode } | ConvertTo-Json -Depth 5 | Set-Content "$stem.resources.json" -Encoding UTF8
    Get-Content "$stem.stdout.log"
    Get-Content "$stem.stderr.log"
    $global:LASTEXITCODE = [int]$exitCode
}
# Reuse the existing DPAPI authentication, identities and authenticated preflight.
$existing = Get-Content -Raw .env.issue21-test.ps1
if (-not $existing.Contains("`$env:STRESS_VUS='200'")) { throw 'Unexpected existing runner VU configuration' }
if (-not $existing.Contains('k6 run --quiet ./load-tests/event-stress-test.js')) { throw 'Unexpected existing k6 invocation' }
$existing = $existing.Replace("`$env:STRESS_VUS='200'", "`$env:STRESS_VUS='400'").Replace('Starting 200 VU x 30 seconds', 'Starting 400 VU x 30 seconds').Replace('k6 run --quiet ./load-tests/event-stress-test.js', 'Invoke-Issue21MeasuredK6')
& ([scriptblock]::Create($existing))

