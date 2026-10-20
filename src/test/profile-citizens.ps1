param([switch]$Offline, [switch]$Steady, [string[]]$CaseId = @())

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$cases = @(
    @{ Id='cpu-0'; Count=0 }, @{ Id='cpu-50'; Count=50 },
    @{ Id='cpu-100-on-1'; Count=100 }, @{ Id='cpu-100-off-1'; Count=100; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-100-on-2'; Count=100 }, @{ Id='cpu-100-off-2'; Count=100; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-100-on-3'; Count=100 }, @{ Id='cpu-100-off-3'; Count=100; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-300'; Count=300 }, @{ Id='cpu-500'; Count=500 }, @{ Id='cpu-1000'; Count=1000 },
    @{ Id='cpu-100-ai-off'; Count=100; Extra=@('-PdebugDisableCitizenAutonomy=true', '-PdebugDisableCitizenBehaviorGoal=true') },
    @{ Id='cpu-100-pop-off'; Count=100; Extra=@('-PdebugDisableCitizenPopulationTick=true') },
    @{ Id='cpu-1000-jfr'; Count=1000; Extra=@('-PgaugePerformanceJfr=true') },
    @{ Id='cpu-50-off'; Count=50; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-300-off'; Count=300; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-500-off'; Count=500; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-1000-off'; Count=1000; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-50-off-v2'; Count=50; Extra=@('-PgaugePerformanceProfile=false') },
    @{ Id='cpu-1000-off-jfr'; Count=1000; Extra=@('-PgaugePerformanceProfile=false', '-PgaugePerformanceJfr=true') }
)
if ($CaseId.Count -gt 0) {
    foreach ($selected in $CaseId) { if ($selected -notin $cases.Id) { throw "Unknown diagnostic case: $selected" } }
    $cases = @($cases | Where-Object { $_.Id -in $CaseId })
}
Push-Location $projectDirectory
try {
    foreach ($case in $cases) {
        $runId = if ($Steady) { "steady-$($case.Id)" } else { $case.Id }
        $arguments = @('-PjeiEnabled=false', '-PgaugePerformance=true', "-PgaugePerformanceCount=$($case.Count)", "-PgaugeTestRunId=$runId")
        if ($Steady) { $arguments += @('-PgaugePerformanceSteady=true', '-PgaugePerformanceSampleTicks=2000') }
        if ($Offline) { $arguments += '--offline' }
        if ($case.Extra) { $arguments += $case.Extra }
        $log = Join-Path $projectDirectory "build/citizen-performance-$runId.log"
        if (Test-Path (Join-Path $projectDirectory "run/gauge-tests/server-$runId/world")) { throw "Use a fresh run ID; isolated world already exists: $runId" }
        Write-Host "Starting diagnostic case $runId"
        $previousPreference = $ErrorActionPreference
        try { $ErrorActionPreference = 'Continue'; & .\gradlew.bat @arguments runGaugeTestServer 2>&1 | Out-File -FilePath $log -Encoding utf8; $code = $LASTEXITCODE }
        finally { $ErrorActionPreference = $previousPreference }
        if ($code -ne 0) { throw "Diagnostic case failed: $($case.Id). See $log" }
        Select-String -LiteralPath $log -Pattern 'CITIZEN_CPU_PROFILE' | ForEach-Object { Write-Host $_.Line }
    }
} finally { Pop-Location }
