param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Z][A-Za-z0-9]{1,31}$')][string]$Name,
    [string]$Directory='', [string]$BasePackage='', [switch]$NoOpen
)
$ErrorActionPreference = 'Stop'
$templateRoot = Split-Path -Parent $PSScriptRoot
$template = Get-Content (Join-Path $templateRoot 'scaffold.json') -Raw | ConvertFrom-Json
if (-not $BasePackage) { $BasePackage = 'com.kaveenk.' + $Name.ToLowerInvariant() }
if ($BasePackage -notmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$') { throw 'Use a lowercase Java package such as com.kaveenk.skytools.' }
$keywords = @('abstract','assert','boolean','break','byte','case','catch','char','class','const','continue','default','do','double','else','enum','extends','final','finally','float','for','goto','if','implements','import','instanceof','int','interface','long','native','new','package','private','protected','public','return','short','static','strictfp','super','switch','synchronized','this','throw','throws','transient','try','void','volatile','while','true','false','null','_')
if (@($BasePackage -split '\.' | Where-Object { $_ -in $keywords }).Count) { throw 'Java keywords cannot be package segments.' }
if (-not $Directory) { $Directory = Join-Path (Split-Path -Parent $templateRoot) $Name }
$destination = [IO.Path]::GetFullPath($Directory)
if ($destination.StartsWith($templateRoot + '\',[StringComparison]::OrdinalIgnoreCase) -or $destination -eq $templateRoot) { throw 'Create the new project outside the template project.' }
if (Test-Path -LiteralPath $destination) { throw "Destination already exists; nothing was changed: $destination" }
$entries = @('src','.vscode','.cursor','.serena\project.yml','.github','dev','scripts','gradle','.editorconfig','.gitattributes','.gitignore','gradle.properties','settings.gradle.kts','build.gradle.kts','versions.properties','gradlew','gradlew.bat','mcdev.cmd','Play.cmd','Test.cmd','Stop.cmd','README.md','AGENTS.md','scaffold.json',($template.name + '.code-workspace'))
$files = foreach ($entry in $entries) {
    $path = Join-Path $templateRoot $entry
    if (-not (Test-Path -LiteralPath $path)) { throw "Template file missing: $entry" }
    if (Test-Path -LiteralPath $path -PathType Container) { Get-ChildItem -LiteralPath $path -Recurse -File -Force | Where-Object {
        $relativeFile = $_.FullName.Substring($templateRoot.Length + 1)
        $relativeFile -notmatch '^(dev[\\/]lab-tools[\\/])?(build|bin|\.gradle|\.git|\.settings)([\\/]|$)' -and
            $relativeFile -notmatch '^(dev[\\/]lab-tools[\\/])?\.(classpath|project)$'
    } }
    else { Get-Item -LiteralPath $path -Force }
}
New-Item -ItemType Directory -Path $destination | Out-Null
foreach ($file in $files) {
    $relative = $file.FullName.Substring($templateRoot.Length + 1)
    $relative = $relative.Replace($template.basePackage.Replace('.','\'),$BasePackage.Replace('.','\')).Replace($template.name,$Name)
    $target = Join-Path $destination $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    if ($file.Extension -eq '.jar') { Copy-Item -LiteralPath $file.FullName -Destination $target }
    else {
        $text = [IO.File]::ReadAllText($file.FullName).Replace($template.basePackage,$BasePackage).Replace($template.name,$Name).Replace($template.name.ToLowerInvariant(),$Name.ToLowerInvariant())
        [IO.File]::WriteAllText($target,$text,[Text.UTF8Encoding]::new($false))
    }
}
$eula = Join-Path $templateRoot 'run\eula.txt'
if ((Test-Path -LiteralPath $eula) -and (Select-String -LiteralPath $eula -Pattern '^eula=true\s*$' -Quiet)) {
    New-Item -ItemType Directory -Path (Join-Path $destination 'run') | Out-Null
    Copy-Item -LiteralPath $eula -Destination (Join-Path $destination 'run\eula.txt')
}
Push-Location $destination
try {
    & git init -b main
    if ($LASTEXITCODE -ne 0) { throw 'Files were created, but Git initialization failed.' }
} finally { Pop-Location }
Write-Host "Created $Name at $destination"
Write-Host 'Next: open the .code-workspace file, edit src/main/java, run Tasks: Run Build Task, then mcdev play.'
if (-not $NoOpen) {
    $cursor = Join-Path $env:LOCALAPPDATA 'Programs\cursor\resources\app\bin\cursor.cmd'
    if (Test-Path $cursor) { & $cursor --new-window (Join-Path $destination ($Name + '.code-workspace')) }
}
