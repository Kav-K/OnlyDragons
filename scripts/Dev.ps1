<#
.SYNOPSIS
Routes editor development actions through the wrapper and Windows lab.
.DESCRIPTION
Finds a JDK first in the process JAVA_HOME, then the user JAVA_HOME, and requires
javac.exe. Updates this process environment, executes from the project root and
restores the prior working directory in finally. Build/Test propagate nonzero
Gradle exits; runtime actions delegate to the same lab used by mcdev.
.PARAMETER Action
Build runs build; Test runs test and jacocoTestReport. Prepare, Run, Debug, Smoke and Updates delegate to their existing lab/update paths.
.EXAMPLE
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Dev.ps1 Build
.NOTES
Run/Debug are human lab entry points and may coordinate other managed development servers. Unattended feature workers use the isolated agent-test runner instead.
#>
param(
    [ValidateSet('Build', 'Test', 'Prepare', 'Run', 'Debug', 'Smoke', 'Updates')]
    [string]$Action = 'Build'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) {
    $jdkHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
}
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) {
    throw 'Install JDK 25 or the version in versions.properties and set JAVA_HOME to its directory.'
}
$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + ';' + $env:Path
Push-Location $projectRoot
try {
    switch ($Action) {
        'Build'   { & .\gradlew.bat build --console=plain }
        'Test'    { & .\gradlew.bat test jacocoTestReport --console=plain }
        'Prepare' { & "$PSScriptRoot\Lab.ps1" prepare; return }
        'Run'     { & "$PSScriptRoot\Lab.ps1" run; return }
        'Debug'   { & "$PSScriptRoot\Lab.ps1" run -DebugServer; return }
        'Smoke'   { & "$PSScriptRoot\Smoke-Test.ps1"; return }
        'Updates' { & "$PSScriptRoot\Check-Updates.ps1"; return }
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}
