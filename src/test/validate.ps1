param(
    [switch]$Offline,
    [switch]$OptionalRecipes,
    [switch]$Destroy,
    [switch]$Escalated
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
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
    if ($OptionalRecipes) {
        $recipeCases = if ($Destroy) { @('A', 'B', 'C', 'D', 'E', 'F') } else { @('A', 'B') }
        foreach ($recipeCase in $recipeCases) {
            Write-Host "Optional recipe validation: case $recipeCase"
            $recipeArguments = $gradleArguments + "-PrecipeTestCase=$recipeCase"
            if ($recipeCase -in @('C', 'D', 'E', 'F')) { $recipeArguments += '-PdestroyEnabled=true' }
            $previousErrorAction = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                & .\gradlew.bat @recipeArguments runRecipeTestServer 2>&1 | ForEach-Object { Write-Host $_ }
                $taskExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $previousErrorAction
            }
            if ($taskExitCode -ne 0) { throw "Optional recipe case $recipeCase failed (exit code $taskExitCode)." }
        }
    }
    if ($Escalated) {
        Write-Host 'Citizen route validation with Escalated'
        $previousErrorAction = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\gradlew.bat @gradleArguments -PescalatedTests=true runRouteTestServer 2>&1 | ForEach-Object { Write-Host $_ }
            $taskExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorAction
        }
        if ($taskExitCode -ne 0) { throw 'Citizen route validation with Escalated failed.' }
    }
    Write-Host 'Datagen, JUnit/build, and dedicated GameTest validation passed.'
} finally {
    Pop-Location
}
