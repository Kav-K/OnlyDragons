param([Parameter(Mandatory=$true)][string]$JdkHome)
$ErrorActionPreference = 'Stop'
$resolvedJdk = (Resolve-Path -LiteralPath $JdkHome).Path
if (-not (Test-Path (Join-Path $resolvedJdk 'bin\javac.exe'))) { throw 'The supplied directory must contain bin\javac.exe.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$settingsPath = Join-Path $projectRoot '.vscode\settings.json'
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$pin = Get-Content (Join-Path $projectRoot 'versions.properties') | Where-Object { $_ -match '^javaVersion=' }
$major = ($pin -split '=',2)[1]
$settings.'java.jdt.ls.java.home' = $resolvedJdk
$settings.'java.import.gradle.java.home' = $resolvedJdk
$settings.'java.configuration.runtimes' = @(@{name="JavaSE-$major";path=$resolvedJdk;default=$true})
$settings.'terminal.integrated.env.windows' = @{JAVA_HOME=$resolvedJdk;Path=((Join-Path $resolvedJdk 'bin') + ';${env:Path}')}
[System.IO.File]::WriteAllText($settingsPath, ($settings | ConvertTo-Json -Depth 8) + "`n", [System.Text.UTF8Encoding]::new($false))
$workspacePath = Join-Path $projectRoot 'OnlyDragons.code-workspace'
$workspace = Get-Content -LiteralPath $workspacePath -Raw | ConvertFrom-Json
foreach ($key in @('java.jdt.ls.java.home', 'java.import.gradle.java.home', 'java.configuration.runtimes', 'terminal.integrated.env.windows')) {
    $workspace.settings | Add-Member -NotePropertyName $key -NotePropertyValue $settings.$key -Force
}
[System.IO.File]::WriteAllText($workspacePath, ($workspace | ConvertTo-Json -Depth 12) + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Host 'Cursor workspace Java paths updated. Reload the Cursor window to apply.'
