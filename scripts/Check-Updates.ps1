param([switch]$WriteCandidate)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pins = @{}
Get-Content (Join-Path $projectRoot 'versions.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $pins[$matches[1]] = $matches[2] }
}
$agent = 'OnlyDragons/1.0 (local development; https://openai.com/codex/)'
$project = Invoke-RestMethod 'https://fill.papermc.io/v3/projects/paper' -UserAgent $agent -TimeoutSec 30
$versions = @($project.versions.PSObject.Properties | ForEach-Object { $_.Value } |
    Where-Object { $_ -match '^\d+(\.\d+)+$' } | Sort-Object { [version]$_ } -Descending)
$latest = $null
foreach ($minecraft in $versions) {
    $builds = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/$minecraft/builds" -UserAgent $agent -TimeoutSec 30
    $latest = $builds | Where-Object channel -eq 'STABLE' | Sort-Object id -Descending | Select-Object -First 1
    if ($latest) { break }
}
if (-not $latest) { throw 'No stable Paper release found.' }
$download = $latest.downloads.'server:default'
$mockArtifact = "mockbukkit-v$minecraft"
$mockVersion = $null
try {
    [xml]$metadata = (Invoke-WebRequest "https://repo.maven.apache.org/maven2/org/mockbukkit/mockbukkit/$mockArtifact/maven-metadata.xml" -UseBasicParsing -TimeoutSec 30).Content
    $mockVersion = $metadata.metadata.versioning.release
} catch {
    Write-Warning "No MockBukkit release found for $minecraft yet; retain your tested version until tests can migrate."
}
Write-Host "Current: Minecraft $($pins.minecraftVersion), Paper build $($pins.paperBuild)"
Write-Host "Latest stable: Minecraft $minecraft, Paper build $($latest.id)"
Write-Host "MockBukkit: $mockArtifact $mockVersion"
Write-Host 'Before upgrading: check the required JDK, back up run/, update pins, build, and run the smoke test.'
if ($WriteCandidate) {
    if (-not $mockVersion) { throw 'Cannot create a complete candidate without a matching MockBukkit release.' }
    $pins.minecraftVersion = $minecraft
    $pins.paperApiVersion = "$minecraft.build.$($latest.id)-stable"
    $pins.paperBuild = [string]$latest.id
    $pins.paperUrl = $download.url
    $pins.paperSha256 = $download.checksums.sha256
    $pins.mockBukkitArtifact = $mockArtifact
    $pins.mockBukkitVersion = [string]$mockVersion
    $output = Join-Path $projectRoot 'build\update-candidate.properties'
    New-Item -ItemType Directory -Path (Split-Path $output) -Force | Out-Null
    $content = @('# Candidate only. Verify Java requirements and review before replacing versions.properties.')
    $content += $pins.Keys | Sort-Object | ForEach-Object { "$_=$($pins[$_])" }
    [System.IO.File]::WriteAllLines($output, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Candidate written to $output"
}
