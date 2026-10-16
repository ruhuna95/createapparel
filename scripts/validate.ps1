param(
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$gradleArguments = @('--console=plain', '-PjeiEnabled=false')
if ($Offline) { $gradleArguments += '--offline' }

Push-Location $projectDirectory
try {
    foreach ($task in @('runData', 'build', 'runGameTestServer')) {
        Write-Host "Validation stage: $task"
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\gradlew.bat @gradleArguments $task 2>&1 | ForEach-Object { Write-Host $_ }
            $taskExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorAction
        }
        if ($taskExitCode -ne 0) {
            throw "Validation failed at $task (exit code $taskExitCode)."
        }
    }
    Write-Host 'Datagen, JUnit/build, and dedicated GameTest validation passed.'
} finally {
    Pop-Location
}
