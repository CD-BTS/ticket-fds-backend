# Runs the full unit/MVC and MySQL suites in an isolated disposable MySQL container.
# Requires an existing local MySQL 8 image. No existing database or .env file is changed.
param([string]$MySqlImage = 'mysql:8.0')
$ErrorActionPreference = 'Stop'
$containerId = $null
$previous = @{}
foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JPA_SHOW_SQL')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    docker image inspect $MySqlImage --format '{{.Id}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'The specified MySQL image must already be available locally.' }
    $testPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $containerName = 'bts-query-tests-' + [Guid]::NewGuid().ToString('N')
    # Match the explicit collation of booking_history/user_card FK columns in the existing schema.
    $containerId = docker run --rm -d --name $containerName -p '127.0.0.1::3306' --env "MYSQL_ROOT_PASSWORD=$testPassword" --env 'MYSQL_DATABASE=fairticket_test' --env 'MYSQL_USER=fairticket_test' --env "MYSQL_PASSWORD=$testPassword" $MySqlImage --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    if ($LASTEXITCODE -ne 0 -or $containerId -notmatch '^[a-f0-9]{64}$') { throw 'Could not start isolated MySQL.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $ErrorActionPreference = 'Continue'
        $readyOutput = 'SELECT 1;' | docker exec -i --env "MYSQL_PWD=$testPassword" $containerId mysql '--host=127.0.0.1' '--connect-timeout=2' -ufairticket_test fairticket_test 2>&1
        $ErrorActionPreference = 'Stop'
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { Write-Output $readyOutput; throw 'Isolated MySQL did not become ready.' }
    $mapping = docker port $containerId '3306/tcp'
    $testPort = ($mapping -split ':')[-1].Trim()
    $schema = Join-Path $PSScriptRoot '../db/schema.sql'
    docker cp $schema ($containerId + ':/tmp/bts-schema.sql') | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy test schema.' }
    'source /tmp/bts-schema.sql;' | docker exec -i --env "MYSQL_PWD=$testPassword" $containerId mysql '--host=127.0.0.1' -uroot fairticket_test
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize isolated schema.' }
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$testPort/fairticket_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
    $env:DB_USERNAME = 'fairticket_test'
    $env:DB_PASSWORD = $testPassword
    $env:JPA_SHOW_SQL = 'false'
    Write-Output 'Running unit/MVC and MySQL suites against an isolated database.'
    & (Join-Path $PSScriptRoot '../gradlew.bat') test mysqlTest --no-daemon --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Test suite failed. See build/reports/tests.' }
} finally {
    foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    if ($containerId -match '^[a-f0-9]{64}$') {
        # Only stop the container created above; --rm removes its temporary data.
        docker stop $containerId | Out-Null
    }
    $testPassword = $null
}
