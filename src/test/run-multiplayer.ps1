param(
    [string]$RunId = ('validation-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()),
    [int]$Port = 25585,
    [switch]$Offline,
    [switch]$ResumeOnly
)

$ErrorActionPreference = 'Stop'
if ($RunId -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid RunId' }
$projectDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$testDirectory = Join-Path $projectDirectory "run/multiplayer-tests/$RunId"
if ((Test-Path -LiteralPath $testDirectory) -and !$ResumeOnly) { throw "Use a new RunId; existing results are preserved: $testDirectory" }
if ($ResumeOnly) {
    foreach ($role in @('server', 'A', 'B')) {
        if (!(Test-Path -LiteralPath (Join-Path $testDirectory "initial-$role-passed.txt"))) { throw 'ResumeOnly requires a completed initial run' }
    }
    if (Test-Path -LiteralPath (Join-Path $testDirectory 'resume-plan.nbt')) { throw 'Existing resume results must be preserved; use a new RunId' }
}

function ConvertTo-NativeArgument([string]$Value) {
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}

Push-Location $projectDirectory
try {
    $phases = if ($ResumeOnly) { @('true') } else { @('false', 'true') }
    foreach ($resume in $phases) {
        $phase = if ($resume -eq 'true') { 'resume' } else { 'initial' }
        $gradleArguments = @('--console=plain', '-PjeiEnabled=false', "-PmultiplayerRunId=$RunId",
            "-PmultiplayerPort=$Port", "-PmultiplayerResume=$resume", 'exportMultiplayerTestLaunches')
        if ($Offline) { $gradleArguments += '--offline' }
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\gradlew.bat @gradleArguments 2>&1 | ForEach-Object { Write-Host $_ }
            $gradleExitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousErrorAction }
        if ($gradleExitCode -ne 0) { throw 'Multiplayer preparation failed' }
        $launches = Get-Content -LiteralPath "build/multiplayer-$RunId.json" -Raw -Encoding UTF8 | ConvertFrom-Json
        $processes = @()
        try {
            foreach ($launch in $launches) {
                foreach ($entry in $launch.environment.PSObject.Properties) {
                    [Environment]::SetEnvironmentVariable($entry.Name, $entry.Value, 'Process')
                }
                $arguments = ($launch.command | Select-Object -Skip 1 | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
                $process = Start-Process -FilePath $launch.command[0] -ArgumentList $arguments -WorkingDirectory $launch.directory `
                    -WindowStyle Hidden -PassThru -RedirectStandardOutput "build/multiplayer-$RunId-$phase-$($launch.role).log" `
                    -RedirectStandardError "build/multiplayer-$RunId-$phase-$($launch.role)-stderr.log"
                [void]$process.Handle
                $processes += $process
                if ($launch.role -eq 'Server') {
                    $deadline = [DateTime]::UtcNow.AddMinutes(4)
                    do {
                        Start-Sleep -Seconds 2
                        if ($process.HasExited) { throw 'Dedicated server exited before accepting connections' }
                        if ([DateTime]::UtcNow -gt $deadline) { throw 'Dedicated server startup timeout' }
                        $serverLog = Join-Path $projectDirectory "build/multiplayer-$RunId-$phase-Server.log"
                    } until ((Test-Path -LiteralPath $serverLog) -and (Select-String -LiteralPath $serverLog -Pattern 'Done \(' -Quiet))
                }
                if ($launch.role -eq 'ClientA') {
                    $deadline = [DateTime]::UtcNow.AddMinutes(4)
                    $serverLog = Join-Path $projectDirectory "build/multiplayer-$RunId-$phase-Server.log"
                    do {
                        Start-Sleep -Seconds 2
                        if ($process.HasExited) { throw 'Client A exited before login' }
                        if ([DateTime]::UtcNow -gt $deadline) { throw 'Client A login timeout' }
                    } until (Select-String -LiteralPath $serverLog -Pattern 'ApparelQAA.*logged in with entity' -Quiet)
                }
            }
            $deadline = [DateTime]::UtcNow.AddMinutes(17)
            while (@($processes | Where-Object { -not $_.HasExited }).Count -gt 0) {
                if ([DateTime]::UtcNow -gt $deadline) { throw "Multiplayer $phase timeout" }
                if (Get-ChildItem -LiteralPath $testDirectory -Filter "$phase-*-failed.txt") { throw "Multiplayer $phase checks failed" }
                Start-Sleep -Seconds 2
            }
            foreach ($role in @('server', 'A', 'B')) {
                if (!(Test-Path -LiteralPath (Join-Path $testDirectory "$phase-$role-passed.txt"))) {
                    throw "Missing multiplayer success result: $phase / $role"
                }
            }
            foreach ($process in $processes) {
                $process.WaitForExit()
                if ($null -ne $process.ExitCode -and $process.ExitCode -ne 0) { throw "Multiplayer process failed: $($process.ExitCode)" }
            }
            Write-Host "Multiplayer $phase passed with two real clients."
        } finally {
            foreach ($process in $processes) {
                if (-not $process.HasExited) { Stop-Process -Id $process.Id }
                $process.Dispose()
            }
        }
    }
} finally {
    Pop-Location
}
