<#
.SYNOPSIS
Loads shared Windows lab paths, metadata and lifecycle helpers.
.DESCRIPTION
Dot-source from lab entry points. Establishes the project root and terminating-error
policy, loads ZIP support and imports process-ownership helpers. These functions read
and write managed profile metadata; a lock probe alone is not process identity proof.
Server starts and broad managed-server cleanup remain caller-controlled operations.
.NOTES
Human lab profiles retain their own worlds and authenticated loopback settings. This module is separate from the isolated unattended Paper runner.
#>
$ErrorActionPreference = 'Stop'
$script:LabRoot = Split-Path -Parent $PSScriptRoot
$script:LabAgent = 'OnlyDragons/1.0 (local development; https://openai.com/codex/)'
Add-Type -AssemblyName System.IO.Compression.FileSystem

<#
.SYNOPSIS
Writes lab text as BOM-less UTF-8.
.DESCRIPTION
Replaces only the supplied file; the caller owns its parent directory and path. Used for profile commands and metadata whose encoding must be stable.
.PARAMETER Path
Owned output file path.
.PARAMETER Text
Complete replacement text.
#>
function Write-LabText($Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
}
<#
.SYNOPSIS
Reads existing JSON metadata or emits no value when absent.
.DESCRIPTION
Parsing failures propagate; callers must distinguish a missing object from an active or verified session.
.PARAMETER Path
Local metadata file to read.
#>
function Read-LabJson($Path) {
    if (Test-Path -LiteralPath $Path) { Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json }
}
<#
.SYNOPSIS
Publishes bounded-depth JSON through a PID-specific sibling file.
.DESCRIPTION
Serializes to UTF-8 and moves the completed sibling over the destination. The caller supplies an owned path; this helper does not verify profile/process identity.
.PARAMETER Path
Owned JSON destination.
.PARAMETER Value
Object serialized with the existing depth-14 schema limit.
#>
function Write-LabJson($Path, $Value) {
    $temporary = "$Path.$PID.tmp"
    Write-LabText $temporary (($Value | ConvertTo-Json -Depth 14) + "`n")
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}
<#
.SYNOPSIS
Reads the checkout's declared version and artifact pins.
.DESCRIPTION
Parses property lines into a fresh map without contacting release services or modifying versions.properties.
#>
function Get-LabPins {
    $result = @{}
    Get-Content (Join-Path $script:LabRoot 'versions.properties') | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { $result[$matches[1]] = $matches[2] }
    }
    return $result
}
<#
.SYNOPSIS
Queries released numeric Paper version names.
.DESCRIPTION
Uses the public Paper API with a bounded request and returns versions in descending numeric order. Availability does not establish plugin compatibility.
#>
function Get-LabVersions {
    $project = Invoke-RestMethod 'https://fill.papermc.io/v3/projects/paper' -UserAgent $script:LabAgent -TimeoutSec 30
    @($project.versions.PSObject.Properties | ForEach-Object { $_.Value } |
        Where-Object { $_ -match '^\d+(\.\d+)+$' } | Sort-Object { [version]$_ } -Descending)
}
<#
.SYNOPSIS
Selects the newest stable or recommended build for an explicit version.
.DESCRIPTION
Queries the public Paper build list and fails when no admitted release channel exists. Profile pin reuse is decided by Prepare-LabServer, not this lookup.
.PARAMETER Version
Numeric Paper version requested by the caller.
#>
function Get-LabPaperBuild([string]$Version) {
    $builds = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds" -UserAgent $script:LabAgent -TimeoutSec 30
    $release = $builds | Where-Object { $_.channel -eq 'STABLE' -or $_.channel -eq 'RECOMMENDED' } | Sort-Object id -Descending | Select-Object -First 1
    if (-not $release) { throw "No stable Paper build is available for $Version. Use a supported release or supply -ServerJar." }
    return $release
}
<#
.SYNOPSIS
Maps a server version to the lab's configured JDK major.
.DESCRIPTION
This compatibility mapping selects a runtime major only. It does not inspect arbitrary server bytecode or prove support for a custom JAR.
.PARAMETER Version
Parseable server version.
#>
function Get-LabJavaVersion([string]$Version) {
    $number = [version]$Version
    if ($number -ge [version]'26.1') { return 25 }
    if ($number -ge [version]'1.20') { return 21 }
    if ($number -ge [version]'1.17') { return 17 }
    if ($number -ge [version]'1.16.5') { return 16 }
    if ($number -ge [version]'1.12') { return 11 }
    return 8
}
<#
.SYNOPSIS
Finds an installed compiler-bearing JDK of the requested major.
.DESCRIPTION
Checks process/user JAVA_HOME and the local Adoptium directory using release metadata. With Install, downloads the current Windows x64 Temurin JDK for that major, verifies the advertised SHA-256 and extracts it before searching again. Existing candidates are major-matched, not independently archive-verified.
.PARAMETER Major
Required JDK major version.
.PARAMETER Install
Permit network download/cache/extraction when no matching installed JDK exists.
#>
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
<#
.SYNOPSIS
Derives a version/profile path beneath this project's run directory.
.DESCRIPTION
Admits numeric versions and simple profile names before path construction, preventing the two identifiers from supplying path traversal.
.PARAMETER Version
Released numeric version.
.PARAMETER Profile
Letters, digits, underscores and hyphens only.
#>
function Get-LabProfile([string]$Version, [string]$Profile) {
    if ($Version -notmatch '^\d+(\.\d+)+$' -or $Profile -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a released numeric version and a profile containing only letters, digits, underscores or hyphens.' }
    Join-Path $script:LabRoot "run\servers\$Version-$Profile"
}
<#
.SYNOPSIS
Probes exclusive ownership of a profile session lock.
.DESCRIPTION
May create session.lock in an existing directory. A successful open means inactive; an IOException conservatively means active. This is a lock test, not verification of a Java PID.
.PARAMETER Directory
Existing managed profile directory, or an absent path treated as inactive.
#>
function Test-LabActive($Directory) {
    if (-not (Test-Path -LiteralPath $Directory)) { return $false }
    try {
        $handle = [IO.File]::Open((Join-Path $Directory 'session.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $handle.Dispose()
        return $false
    } catch [IO.IOException] { return $true }
}
<#
.SYNOPSIS
Checks whether an IPv4 loopback port can be bound now.
.DESCRIPTION
Starts and immediately stops a temporary listener even on failure. The check does not reserve the port for the later Java launch.
.PARAMETER Port
Game or debugger port to probe.
#>
function Assert-LabPort([int]$Port) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start() } catch { throw "Local port $Port is in use. Stop the existing server or choose -Port / -DebugPort." } finally { $listener.Stop() }
}
<#
.SYNOPSIS
Queues one console line for an active profile worker.
.DESCRIPTION
Rejects empty/multiline input and strips a leading slash. Publishes a unique command file only after writing it completely; the worker owns forwarding/deletion. Submission alone does not prove the command was processed.
.PARAMETER Directory
Active profile whose commands inbox receives the file.
.PARAMETER Command
Single console command supplied by the operator or smoke plan.
#>
function Send-LabCommand($Directory, [string]$Command) {
    if (-not (Test-LabActive $Directory)) { throw 'This profile is not running.' }
    if ($Command -match "[\r\n]" -or [string]::IsNullOrWhiteSpace($Command)) { throw 'Supply one nonempty console command.' }
    $inbox = Join-Path $Directory 'commands'
    $id = [guid]::NewGuid().ToString('N')
    $temporary = Join-Path $inbox "$id.tmp"
    Write-LabText $temporary ($Command.TrimStart('/') + "`n")
    Move-Item -LiteralPath $temporary -Destination (Join-Path $inbox "$id.cmd")
}
<#
.SYNOPSIS
Requests graceful world-saving shutdown of one active profile.
.DESCRIPTION
Queues stop, waits for the lock to release, then requires stopped state and Java exit 0. A timeout throws without terminating another process; identity-checked forced recovery is a separate helper.
.PARAMETER Directory
Profile to stop.
.PARAMETER Timeout
Maximum seconds to wait for graceful lock release; defaults to 90.
#>
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
<#
.SYNOPSIS
Reads plugin identity, minimum class version and hash without loading the JAR.
.DESCRIPTION
Prefers paper-plugin.yml over plugin.yml and supports the existing simple scalar descriptor fields. Scans ordinary class headers, excluding multi-release entries and module-info, then returns the SHA-256 and compatibility metadata. This is admission metadata, not a full YAML parser or runtime compatibility proof.
.PARAMETER Path
Existing plugin JAR to inspect; archive/entry streams are disposed on failure too.
#>
function Get-LabPlugin($Path) {
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $zip = [IO.Compression.ZipFile]::OpenRead($resolved)
    try {
        $descriptor = $zip.GetEntry('paper-plugin.yml')
        if (-not $descriptor) { $descriptor = $zip.GetEntry('plugin.yml') }
        if (-not $descriptor) { throw "$resolved is not a Paper/Bukkit plugin JAR (no plugin descriptor)." }
        $reader = [IO.StreamReader]::new($descriptor.Open())
        try { $yaml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        <#
        .SYNOPSIS
        Reads one simple top-level plugin descriptor scalar.
        .DESCRIPTION
        Uses the enclosing descriptor text, removes a trailing inline comment and trims quotes. Complex YAML structures are outside this metadata reader.
        .PARAMETER Key
        Literal descriptor key, escaped before regex matching.
        #>
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
<#
.SYNOPSIS
Resolves an explicit JAR file or the immediate JAR files in a directory.
.DESCRIPTION
Does not recursively scan dependency trees or validate plugin descriptors; Get-LabPlugin performs the later admission checks.
.PARAMETER Path
Leaf file or directory supplied by the caller.
#>
function Get-LabJarPaths([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Container) {
        return @(Get-ChildItem -LiteralPath $Path -Filter '*.jar' -File | ForEach-Object FullName)
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "JAR or directory not found: $Path" }
    return @((Resolve-Path -LiteralPath $Path).Path)
}
<#
.SYNOPSIS
Reads the worker's console log explicitly as UTF-8.
.DESCRIPTION
Returns empty text before the log exists. Explicit decoding preserves Java stdout/stderr Unicode on Windows PowerShell independently of the OEM code page.
.PARAMETER Directory
Profile whose console.log is read.
#>
function Read-LabLog($Directory) {
    $log = Join-Path $Directory 'console.log'
    if (Test-Path -LiteralPath $log) { return [string](Get-Content -LiteralPath $log -Raw -Encoding UTF8) }
    return ''
}
<#
.SYNOPSIS
Waits for a regex in log text written after a captured character offset.
.DESCRIPTION
Re-reads UTF-8 text until matching, profile exit or timeout. Offsets prevent earlier startup/command output from satisfying a new check; a match itself is only console evidence.
.PARAMETER Directory
Active profile with console.log.
.PARAMETER Pattern
Expected regular expression.
.PARAMETER Timeout
Maximum wait seconds, default 30.
.PARAMETER Offset
Character count captured before submitting the command, not a byte offset.
#>
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
<#
.SYNOPSIS
Extracts known server/plugin failure lines from captured console text.
.DESCRIPTION
Matches the existing ERROR/SEVERE, lab-check and startup/enable/disable signatures. It is a bounded signature screen, not a guarantee that all possible failures appear in logs.
.PARAMETER Text
Complete captured console text to screen.
#>
function Get-LabErrors([string]$Text) {
    @($Text -split "`n" | Where-Object { $_ -match '(?:\bERROR\]|/ERROR\]|\bSEVERE\]|MCDEV_CHECK_FAILED|Error occurred while (?:enabling|disabling)|Could not load|Failed to start the minecraft server)' })
}

. "$PSScriptRoot\Lab.Processes.ps1"
