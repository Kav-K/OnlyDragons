$ErrorActionPreference = 'Stop'
$script:LabRoot = Split-Path -Parent $PSScriptRoot
$script:LabAgent = 'OnlyDragons/1.0 (local development; https://openai.com/codex/)'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Write-LabText($Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}
function Read-LabJson($Path) {
    if (Test-Path -LiteralPath $Path) { Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json }
}
function Write-LabJson($Path, $Value) {
    $temporary = "$Path.$PID.tmp"
    Write-LabText $temporary (($Value | ConvertTo-Json -Depth 14) + "`n")
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}
function Get-LabPins {
    $result = @{}
    Get-Content (Join-Path $script:LabRoot 'versions.properties') | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { $result[$matches[1]] = $matches[2] }
    }
    return $result
}
function Get-LabVersions {
    $project = Invoke-RestMethod 'https://fill.papermc.io/v3/projects/paper' -UserAgent $script:LabAgent -TimeoutSec 30
    @($project.versions.PSObject.Properties | ForEach-Object { $_.Value } |
        Where-Object { $_ -match '^\d+(\.\d+)+$' } | Sort-Object { [version]$_ } -Descending)
}
function Get-LabPaperBuild([string]$Version) {
    $builds = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds" -UserAgent $script:LabAgent -TimeoutSec 30
    $release = $builds | Where-Object { $_.channel -eq 'STABLE' -or $_.channel -eq 'RECOMMENDED' } | Sort-Object id -Descending | Select-Object -First 1
    if (-not $release) { throw "No stable Paper build is available for $Version. Use a supported release or supply -ServerJar." }
    return $release
}
function Get-LabJavaVersion([string]$Version) {
    $number = [version]$Version
    if ($number -ge [version]'26.1') { return 25 }
    if ($number -ge [version]'1.20') { return 21 }
    if ($number -ge [version]'1.17') { return 17 }
    if ($number -ge [version]'1.16.5') { return 16 }
    if ($number -ge [version]'1.12') { return 11 }
    return 8
}
function Get-LabJdk([int]$Major, [switch]$Install) {
    $jdkRoot = Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'
    $candidates = @($env:JAVA_HOME, [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'))
    if (Test-Path -LiteralPath $jdkRoot) { $candidates += Get-ChildItem -LiteralPath $jdkRoot -Directory | ForEach-Object FullName }
    foreach ($candidate in ($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe'))) { continue }
        $release = Join-Path $candidate 'release'
        if (Test-Path -LiteralPath $release) {
            $match = [regex]::Match([IO.File]::ReadAllText($release), 'JAVA_VERSION="(?:1\.)?(\d+)')
            if ($match.Success -and [int]$match.Groups[1].Value -eq $Major) { return $candidate }
        }
    }
    if (-not $Install) { throw "JDK $Major not found. Run mcdev prepare to install its verified Temurin runtime." }
    Write-Host "Installing Temurin JDK $Major for this server version..."
    $ProgressPreference = 'SilentlyContinue'
    $assets = Invoke-RestMethod "https://api.adoptium.net/v3/assets/latest/$Major/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse" -TimeoutSec 60
    $asset = @($assets)[0]
    if (-not $asset.binary.package.link) { throw "Temurin JDK $Major is unavailable. Supply an installed matching JDK." }
    $cache = Join-Path $script:LabRoot 'run\downloads'
    New-Item -ItemType Directory -Path $cache,$jdkRoot -Force | Out-Null
    $archive = Join-Path $cache $asset.binary.package.name
    if (-not (Test-Path $archive) -or (Get-FileHash $archive -Algorithm SHA256).Hash -ne $asset.binary.package.checksum) {
        Invoke-WebRequest $asset.binary.package.link -OutFile $archive -UseBasicParsing -TimeoutSec 300
    }
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $asset.binary.package.checksum) { throw 'JDK checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $jdkRoot -Force
    Get-LabJdk $Major
}
function Get-LabProfile([string]$Version, [string]$Profile) {
    if ($Version -notmatch '^\d+(\.\d+)+$' -or $Profile -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a released numeric version and a profile containing only letters, digits, underscores or hyphens.' }
    Join-Path $script:LabRoot "run\servers\$Version-$Profile"
}
function Test-LabActive($Directory) {
    if (-not (Test-Path -LiteralPath $Directory)) { return $false }
    try {
        $handle = [IO.File]::Open((Join-Path $Directory 'session.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $handle.Dispose()
        return $false
    } catch [IO.IOException] { return $true }
}
function Assert-LabPort([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw "Local port $Port is in use. Stop the existing server or choose -Port / -DebugPort." } finally { $listener.Stop() }
}
function Send-LabCommand($Directory, [string]$Command) {
    if (-not (Test-LabActive $Directory)) { throw 'This profile is not running.' }
    if ($Command -match "[\r\n]" -or [string]::IsNullOrWhiteSpace($Command)) { throw 'Supply one nonempty console command.' }
    $inbox = Join-Path $Directory 'commands'
    $id = [guid]::NewGuid().ToString('N')
    $temporary = Join-Path $inbox "$id.tmp"
    Write-LabText $temporary ($Command.TrimStart('/') + "`n")
    Move-Item -LiteralPath $temporary -Destination (Join-Path $inbox "$id.cmd")
}
function Stop-LabServer($Directory, [int]$Timeout = 90) {
    if (-not (Test-LabActive $Directory)) { return }
    Send-LabCommand $Directory 'stop'
    $deadline = [DateTime]::UtcNow.AddSeconds($Timeout)
    while (Test-LabActive $Directory) {
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Server did not shut down. Inspect its log; no unrelated process was terminated.' }
        Start-Sleep -Milliseconds 200
    }
    $state = Read-LabJson (Join-Path $Directory 'state.json')
    if ($state.status -ne 'stopped' -or $state.exitCode -ne 0) { throw "Server shutdown failed: $($state.status). Inspect $Directory\console.log" }
    Write-Host 'Server stopped cleanly.'
}
function Get-LabPlugin($Path) {
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $zip = [IO.Compression.ZipFile]::OpenRead($resolved)
    try {
        $descriptor = $zip.GetEntry('paper-plugin.yml')
        if (-not $descriptor) { $descriptor = $zip.GetEntry('plugin.yml') }
        if (-not $descriptor) { throw "$resolved is not a Paper/Bukkit plugin JAR (no plugin descriptor)." }
        $reader = [IO.StreamReader]::new($descriptor.Open())
        try { $yaml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        function YamlScalar([string]$Key) {
            $match = [regex]::Match($yaml, ('(?m)^' + [regex]::Escape($Key) + ':\s*([^\r\n]+)'))
            if ($match.Success) { return ($match.Groups[1].Value -replace '\s+#.*$', '').Trim().Trim("'", '"') }
            return ''
        }
        $name = YamlScalar 'name'
        if ($name -notmatch '^[A-Za-z0-9_.-]+$') { throw "Unsupported or missing plugin name in $resolved. Use a literal name in the plugin descriptor." }
        $maxClass = 0
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notlike '*.class' -or $entry.FullName -like 'META-INF/versions/*' -or $entry.Name -eq 'module-info.class') { continue }
            $stream = $entry.Open()
            try {
                $bytes = New-Object byte[] 8
                if ($stream.Read($bytes,0,8) -eq 8) { $maxClass = [Math]::Max($maxClass, (($bytes[6] -shl 8) -bor $bytes[7])) }
            } finally { $stream.Dispose() }
        }
        return [pscustomobject]@{name=$name; version=(YamlScalar 'version'); apiVersion=(YamlScalar 'api-version'); path=$resolved; sha256=(Get-FileHash $resolved -Algorithm SHA256).Hash.ToLower(); javaVersion=[Math]::Max(8, $maxClass - 44)}
    } finally { $zip.Dispose() }
}
function Get-LabJarPaths([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Container) {
        return @(Get-ChildItem -LiteralPath $Path -Filter '*.jar' -File | ForEach-Object FullName)
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "JAR or directory not found: $Path" }
    return @((Resolve-Path -LiteralPath $Path).Path)
}
function Read-LabLog($Directory) {
    $log = Join-Path $Directory 'console.log'
    if (Test-Path -LiteralPath $log) { return [string](Get-Content -LiteralPath $log -Raw -Encoding UTF8) }
    return ''
}
function Wait-LabText($Directory, [string]$Pattern, [int]$Timeout = 30, [int]$Offset = 0) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Timeout)
    while ($true) {
        $text = Read-LabLog $Directory
        if ($text.Length -ge $Offset -and $text.Substring($Offset) -match $Pattern) { return }
        if (-not (Test-LabActive $Directory)) { throw "Server exited while waiting for: $Pattern. Inspect $Directory\console.log" }
        if ([DateTime]::UtcNow -gt $deadline) { throw "Timed out waiting for: $Pattern. Inspect $Directory\console.log" }
        Start-Sleep -Milliseconds 150
    }
}
function Get-LabErrors([string]$Text) {
    @($Text -split "`n" | Where-Object { $_ -match '(?:\bERROR\]|/ERROR\]|\bSEVERE\]|MCDEV_CHECK_FAILED|Error occurred while (?:enabling|disabling)|Could not load|Failed to start the minecraft server)' })
}

. "$PSScriptRoot\Lab.Processes.ps1"
