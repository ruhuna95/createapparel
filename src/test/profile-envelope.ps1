param(
    [int[]]$Counts = @(50, 100, 300, 500, 1000),
    [switch]$Offline,
    [switch]$Reverse,
    [switch]$Images
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Push-Location $projectRoot
try {
    foreach ($count in $Counts) {
        if ($count -notin @(50, 100, 300, 500, 1000)) { throw "Invalid Citizen count: $count" }
        $suffix = if ($Reverse) { '-reverse' } else { '' }
        $outputLog = Join-Path $projectRoot "build/envelope-$count$suffix.log"
        $clientLog = Join-Path $projectRoot "build/envelope-$count$suffix-client.log"
        if ((Test-Path -LiteralPath $outputLog) -or (Test-Path -LiteralPath $clientLog)) { throw "Existing results must be preserved: $outputLog" }
        $arguments = @('-PjeiEnabled=false', '-PgaugePerformanceClient=true', "-PgaugePerformanceCount=$count",
            '-PgaugePerformanceCaseCount=3', '-PgaugeEnvelopeComparison=true',
            "-PgaugeEnvelopeReverse=$($Reverse.IsPresent.ToString().ToLowerInvariant())",
            "-PgaugeEnvelopeImages=$($Images.IsPresent.ToString().ToLowerInvariant())", 'runGaugeTestClient')
        if ($Offline) { $arguments += '--offline' }
        Write-Output "Envelope comparison: $count Citizens, reverse=$Reverse"
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat @arguments *> $outputLog
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Copy-Item -LiteralPath 'run/gauge-tests/client/logs/latest.log' -Destination $clientLog
        if ($exitCode -ne 0) { throw "Envelope comparison failed ($exitCode): $outputLog" }
        if (!(Select-String -LiteralPath $clientLog -SimpleMatch 'CITIZEN_PERFORMANCE_CLIENT_PASSED' -Quiet)) { throw "Client success marker missing: $clientLog" }
        Write-Output "Envelope comparison passed: $clientLog"
    }
} finally {
    Pop-Location
}
