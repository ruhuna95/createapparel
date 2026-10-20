param(
    [int[]]$Counts = @(50, 100, 300, 500, 1000),
    [switch]$Offline,
    [switch]$Images,
    [switch]$Reverse,
    [switch]$Reload,
    [string]$Run = 'main'
)

$ErrorActionPreference = 'Stop'
if ($Run -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid run name' }
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $projectRoot
try {
    foreach ($count in $Counts) {
        if ($count -notin @(50, 100, 300, 500, 1000)) { throw "Invalid Citizen count: $count" }
        $outputLog = Join-Path $projectRoot "build/citizen-lod-$count-$Run.log"
        $clientLog = Join-Path $projectRoot "build/citizen-lod-$count-$Run-client.log"
        if ((Test-Path -LiteralPath $outputLog) -or (Test-Path -LiteralPath $clientLog)) { throw "Existing results must be preserved: $outputLog" }
        $arguments = @('-PjeiEnabled=false', '-PgaugePerformanceClient=true', "-PgaugePerformanceCount=$count",
            '-PgaugeLodComparison=true', '-PgaugePerformanceCaseCount=9', "-PgaugeLodImages=$($Images.IsPresent.ToString().ToLowerInvariant())",
            "-PgaugeLodReverse=$($Reverse.IsPresent.ToString().ToLowerInvariant())", "-PgaugeResourceReload=$($Reload.IsPresent.ToString().ToLowerInvariant())", 'runGaugeTestClient')
        if ($Offline) { $arguments += '--offline' }
        Write-Output "Citizen LOD comparison: $count Citizens"
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat @arguments *> $outputLog
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Copy-Item -LiteralPath 'run/gauge-tests/client/logs/latest.log' -Destination $clientLog
        if ($exitCode -ne 0) { throw "Citizen LOD comparison failed ($exitCode): $outputLog" }
        if (!(Select-String -LiteralPath $clientLog -SimpleMatch 'CITIZEN_PERFORMANCE_CLIENT_PASSED' -Quiet)) { throw "Success marker missing: $clientLog" }
        Write-Output "Citizen LOD comparison passed: $clientLog"
    }
} finally {
    Pop-Location
}
