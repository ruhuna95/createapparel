param(
    [ValidateSet(50, 100, 300, 500, 1000)][int[]]$Counts = @(50, 100, 300, 500, 1000),
    [string]$Cases = 'fixed-3d,approach-3d,fixed-mesh,approach-mesh,fixed-impostor,approach-impostor',
    [switch]$Images,
    [switch]$Pipeline,
    [switch]$PipelineImages,
    [switch]$PipelineResourcePack,
    [ValidateRange(0, 1200)][int]$WarmupTicks = 0,
    [ValidateRange(200, 12000)][int]$SampleTicks = 200,
    [switch]$Reload,
    [switch]$FramesOnly,
    [switch]$Offline,
    [switch]$Run
)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$project = Split-Path -Parent $project
Push-Location $project
try {
    foreach ($count in $Counts) {
        $arguments = @('-PjeiEnabled=false', '-PgaugePerformanceClient=true', "-PgaugePerformanceCount=$count", '-PgaugePerformanceCaseCount=1',
            '-PgaugeMovementClient=true', "-PgaugeMovementCases=$Cases", "-PgaugeRepresentationImages=$($Images.IsPresent.ToString().ToLowerInvariant())",
            "-PgaugePipeline=$($Pipeline.IsPresent.ToString().ToLowerInvariant())", "-PgaugePipelineImages=$($PipelineImages.IsPresent.ToString().ToLowerInvariant())",
            "-PgaugePipelineResourcePack=$($PipelineResourcePack.IsPresent.ToString().ToLowerInvariant())", "-PgaugeMovementSampleTicks=$SampleTicks",
            "-PgaugeMovementReload=$($Reload.IsPresent.ToString().ToLowerInvariant())", "-PgaugeMovementFramesOnly=$($FramesOnly.IsPresent.ToString().ToLowerInvariant())",
            'runGaugeTestClient')
        if ($Offline) { $arguments += '--offline' }
        if ($WarmupTicks -gt 0) { $arguments += "-PgaugeMovementWarmupTicks=$WarmupTicks" }
        Write-Host ".\gradlew.bat $($arguments -join ' ')"
        if (!$Run) { continue }
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $log = Join-Path $project "build/citizen-movement-$count-$stamp.log"
        $started = Get-Date
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\gradlew.bat @arguments *> $log
            $runExitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousErrorAction }
        if ($runExitCode -ne 0) { throw "Movement checks failed: $log" }
        $client = Join-Path $project 'run/gauge-tests/client/logs/latest.log'
        if ((Get-Item -LiteralPath $client).LastWriteTime -lt $started) { throw 'Client log is stale' }
        $contents = [IO.File]::ReadAllText($client)
        if (!$contents.Contains('CITIZEN_MOVEMENT_PASSED')) { throw 'Missing movement success marker' }
        Copy-Item -LiteralPath $client -Destination (Join-Path $project "build/citizen-movement-$count-$stamp-client.log")
        Write-Host "Recorded $log"
    }
} finally { Pop-Location }
