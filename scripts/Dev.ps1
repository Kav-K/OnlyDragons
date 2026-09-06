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
