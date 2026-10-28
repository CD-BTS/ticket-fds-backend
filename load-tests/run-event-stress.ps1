param(
    [ValidateSet(200, 500, 1000)][int]$MaxVus = 1000,
    [ValidateRange(1, 3600)][int]$DurationSeconds = 60
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $env:DURATION_SECONDS = "$DurationSeconds"
    foreach ($level in @(200, 500, 1000)) {
        if ($level -gt $MaxVus) { break }
        $env:STRESS_VUS = "$level"
        $env:RUN_ID = [guid]::NewGuid().ToString()
        Write-Host "STRESS_VUS=$level RUN_ID=$env:RUN_ID"
        k6 run ./load-tests/event-stress-test.js
        $runExitCode = $LASTEXITCODE
        if ($runExitCode -ne 0) { throw "k6 failed (exit=$runExitCode). Higher levels will not run." }
        if ($level -lt $MaxVus) {
            $answer = Read-Host 'Check server logs, DB counts and recovery. Enter NEXT to run the higher level; anything else stops'
            if ($answer -cne 'NEXT') { break }
        }
    }
} finally { Pop-Location }
