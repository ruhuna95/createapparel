param(
    [int[]]$Counts = @(50, 100, 300, 500, 1000),
    [string[]]$Cases = @('submission-before', 'submission-after'),
    [switch]$Offline,
    [switch]$Images,
    [switch]$Reverse,
    [switch]$Reload,
    [string]$Run = 'main'
)

$ErrorActionPreference = 'Stop'
if ($Run -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid run name' }
if ($Cases.Count -eq 0 -or ($Cases | Where-Object { $_ -notmatch '^[a-z0-9-]+$' })) { throw 'Invalid cases' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $projectRoot
try {
    foreach ($count in $Counts) {
        if ($count -notin @(50, 100, 300, 500, 1000)) { throw "Invalid Citizen count: $count" }
        $outputLog = Join-Path $projectRoot "build/citizen-submission-$count-$Run.log"
        $clientLog = Join-Path $projectRoot "build/citizen-submission-$count-$Run-client.log"
        if ((Test-Path -LiteralPath $outputLog) -or (Test-Path -LiteralPath $clientLog)) { throw "Existing results must be preserved: $outputLog" }
        $arguments = @('-PjeiEnabled=false', '-PgaugePerformanceClient=true', "-PgaugePerformanceCount=$count",
            '-PgaugeSubmissionComparison=true', "-PgaugeSubmissionCases=$($Cases -join ',')", "-PgaugePerformanceCaseCount=$($Cases.Count)",
            "-PgaugeLodImages=$($Images.IsPresent.ToString().ToLowerInvariant())", "-PgaugeLodReverse=$($Reverse.IsPresent.ToString().ToLowerInvariant())",
            "-PgaugeResourceReload=$($Reload.IsPresent.ToString().ToLowerInvariant())", 'runGaugeTestClient')
        if ($Offline) { $arguments += '--offline' }
        Write-Output "Citizen submission comparison: $count Citizens"
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat @arguments *> $outputLog
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Copy-Item -LiteralPath 'run/gauge-tests/client/logs/latest.log' -Destination $clientLog
        if ($exitCode -ne 0) { throw "Citizen submission comparison failed ($exitCode): $outputLog" }
        if (!(Select-String -LiteralPath $clientLog -SimpleMatch 'CITIZEN_PERFORMANCE_CLIENT_PASSED' -Quiet)) { throw "Success marker missing: $clientLog" }
        Write-Output "Citizen submission comparison passed: $clientLog"
    }
} finally { Pop-Location }
