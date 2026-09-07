<#
.SYNOPSIS
Manages version-isolated Windows development profiles and smoke checks.
.DESCRIPTION
Routes human preparation, start, Play, restart, console, status and smoke/matrix work.
Defaults to project version pins. New profiles receive loopback authenticated server
properties; existing worlds/configuration remain in their version/profile directory.
Play/restart serialize startup and stop all discovered managed development servers
across projects. Start/run exclude their own profile and may reuse it without rebuilding.
Smoke uses a separate default profile/port, runs sequential console assertions and
records protocol status, plugin checks and clean shutdown. It does not log in a player.
Smoke reports replace the prior report for that version/profile, so preserve evidence
before another invocation when its original identity matters.
.PARAMETER Action
Operation to perform. Run follows logs after startup; closing that caller does not stop the detached server. New delegates project creation.
.PARAMETER Version
Numeric server version; empty uses versions.properties. The explicit latest value resolves a stable release through the public API.
.PARAMETER Profile
Letters, digits, underscore or hyphen profile name; defaults to smoke for smoke/matrix and dev otherwise.
.PARAMETER PluginPath
Plugin JAR or directory. Empty or project selects the wrapper-produced artifact; supplying a path builds only the lab harness unless SkipBuild is set.
.PARAMETER DependencyPath
Additional JAR or directory staged as dependencies after duplicate-name and compatibility checks.
.PARAMETER Checks
JSON console commands and expected regexes for smoke. The project check plan is selected automatically only for project smoke without an override.
.PARAMETER ServerJar
Explicit custom server JAR whose hash becomes the profile pin; no Spigot or older-version compatibility is inferred merely from preparation.
.PARAMETER JavaVersion
Server JDK major override; zero uses the version-to-major mapping. Build JDK selection remains based on project pins.
.PARAMETER Port
Loopback game port; defaults to 25565, or 25566 for smoke/matrix unless explicitly supplied.
.PARAMETER DebugPort
Loopback JDWP port when DebugServer is enabled.
.PARAMETER MemoryMb
Maximum server heap in MiB, admitted from 512 through 16384; it is not a host memory reservation.
.PARAMETER TimeoutSeconds
Startup deadline in seconds; individual command waits and graceful shutdown use their own bounds.
.PARAMETER DebugServer
Enable loopback debugging for newly prepared sessions; reusing a session requires a matching debug port.
.PARAMETER SkipBuild
Reuse existing build artifacts without compiling or rerunning tests. JAR metadata and profile pin checks still run.
.PARAMETER UpdateServer
Explicitly replace a profile's pinned server selection; does not make an existing world safe to downgrade.
.PARAMETER Command
One nonempty console command for console, without embedded newlines; a leading slash is stripped.
.PARAMETER Versions
Comma-separated distinct target versions for sequential matrix smoke invocations.
.PARAMETER Name
Project name passed to New-Project only for new.
.PARAMETER Directory
New project destination only; normal lab profiles are derived from Version and Profile.
.PARAMETER BasePackage
Optional Java package override for new.
.PARAMETER NoOpen
Suppress opening Cursor after new project creation.
.EXAMPLE
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Lab.ps1 smoke
.NOTES
These human-profile commands may affect existing managed sessions. Unattended Symphony workers use scripts/agent-tests/paper_test.py with its isolated profiles and shared lease. This script never records new EULA acceptance.
#>
param(
    [Parameter(Position=0)][ValidateSet('help','new','versions','prepare','start','run','stop','restart','play','join','smoke','matrix','console','logs','status','debug-test')][string]$Action='help',
    [string]$Version='', [string]$Profile='', [string]$PluginPath='', [string]$DependencyPath='',
    [string]$Checks='', [string]$ServerJar='', [int]$JavaVersion=0,
    [ValidateRange(1024,65535)][int]$Port=25565, [ValidateRange(1024,65535)][int]$DebugPort=5005,
    [ValidateRange(512,16384)][int]$MemoryMb=2048, [int]$TimeoutSeconds=240,
    [switch]$DebugServer, [switch]$SkipBuild, [switch]$UpdateServer,
    [string]$Command='', [string]$Versions='',
    [string]$Name='', [string]$Directory='', [string]$BasePackage='', [switch]$NoOpen
)
. "$PSScriptRoot\Lab.Common.ps1"
. "$PSScriptRoot\Lab.Prepare.ps1"
if ($PluginPath -eq 'project') { $PluginPath = '' }
if (-not $PSBoundParameters.ContainsKey('Port') -and $Action -in @('smoke','matrix')) { $Port = 25566 }
if ($Action -eq 'new') {
    if (-not $Name) { throw 'Supply -Name, for example: mcdev new -Name SkyTools' }
    & "$PSScriptRoot\New-Project.ps1" -Name $Name -Directory $Directory -BasePackage $BasePackage -NoOpen:$NoOpen
    exit 0
}
if ($Action -eq 'help') {
    Write-Host @'
OnlyDragons plugin lab
  .\mcdev new -Name SkyTools           Create a separate plugin project and open it in Cursor
  .\mcdev play                        Stop existing dev servers, build, start, and show connection details
  .\mcdev restart                     Rebuild and restart after edits
  .\mcdev stop                        Save worlds and stop the server
  .\mcdev smoke                       Build and run real-server assertions, then stop
  .\mcdev start -DebugServer           Start with Cursor debugger port 5005
  .\mcdev console -Command "op Name"   Send a server console command
  .\mcdev logs                        Show current server output
  .\mcdev versions                    List downloadable released Paper versions
  .\mcdev play -Version 1.21.11 -PluginPath C:\Plugins\Example.jar
  .\mcdev smoke -Version 26.2 -PluginPath C:\Plugins -Checks dev\checks\example.json
  .\mcdev matrix -Versions "1.21.11,26.1.2,26.2" -PluginPath C:\Plugins\Example.jar

Optional: -DependencyPath (JAR or directory), -Profile name, -Port number,
  -ServerJar (your Spigot/custom server), -JavaVersion number, -MemoryMb number.
Profiles isolate worlds/configuration by version. Artifacts must support each target.
'@
    exit 0
}
if ($Action -eq 'versions') { Get-LabVersions; exit 0 }
$pins = Get-LabPins
if (-not $Version) { $Version = $pins.minecraftVersion }
if ($Version -eq 'latest') {
    foreach ($candidate in (Get-LabVersions)) {
        try { $null = Get-LabPaperBuild $candidate; $Version = $candidate; break } catch { }
    }
    if ($Version -eq 'latest') { throw 'No stable version could be resolved.' }
}
if (-not $Profile) { $Profile = if ($Action -in @('smoke','matrix')) {'smoke'} else {'dev'} }
if ($Action -eq 'matrix') {
    if (-not $Versions) { throw 'Supply -Versions "1.21.11,26.1.2,26.2" for the versions your plugin supports.' }
    $results = @()
    foreach ($target in ($Versions -split ',' | ForEach-Object Trim | Select-Object -Unique)) {
        $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File', $PSCommandPath, 'smoke','-Version',$target,'-Profile',$Profile,'-Port',[string]$Port,'-MemoryMb',[string]$MemoryMb,'-TimeoutSeconds',[string]$TimeoutSeconds)
        if ($PluginPath) { $arguments += @('-PluginPath',$PluginPath) }
        if ($DependencyPath) { $arguments += @('-DependencyPath',$DependencyPath) }
        if ($Checks) { $arguments += @('-Checks',$Checks) }
        if ($ServerJar) { $arguments += @('-ServerJar',$ServerJar) }
        if ($JavaVersion) { $arguments += @('-JavaVersion',[string]$JavaVersion) }
        if ($UpdateServer) { $arguments += '-UpdateServer' }
        if ($SkipBuild) { $arguments += '-SkipBuild' }
        & powershell.exe @arguments
        $results += [pscustomobject]@{version=$target;passed=($LASTEXITCODE -eq 0);exitCode=$LASTEXITCODE}
    }
    $reportRoot = Join-Path $script:LabRoot 'build\reports\lab'
    New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
    Write-LabJson (Join-Path $reportRoot 'matrix.json') $results
    $results | Format-Table -AutoSize
    if (@($results | Where-Object { -not $_.passed }).Count) { throw 'Compatibility matrix failed; see build/reports/lab/matrix.json.' }
    exit 0
}
$directory = Get-LabProfile $Version $Profile
if ($Action -eq 'stop') { Stop-LabServer $directory; exit 0 }
if ($Action -eq 'console') { Send-LabCommand $directory $Command; Start-Sleep -Milliseconds 400; Get-Content (Join-Path $directory 'console.log') -Tail 12 -Encoding UTF8; exit 0 }
if ($Action -eq 'logs') { Get-Content (Join-Path $directory 'console.log') -Tail 60 -Encoding UTF8; exit 0 }
if ($Action -eq 'status') {
    [pscustomobject]@{version=$Version;profile=$Profile;running=(Test-LabActive $directory);state=(Read-LabJson (Join-Path $directory 'state.json'));directory=$directory} | ConvertTo-Json -Depth 6
    exit 0
}
if ($Action -eq 'join') {
    if (-not (Test-LabActive $directory)) { throw 'Start this server before joining.' }
    $session = Read-LabJson (Join-Path $directory 'session.json')
    Write-Host "Open Minecraft Java $Version using any launcher. Multiplayer > Direct Connection > 127.0.0.1:$($session.port)"
    exit 0
}
if ($Action -eq 'debug-test') {
    $session = Read-LabJson (Join-Path $directory 'session.json')
    if (-not (Test-LabActive $directory) -or -not $session.debugPort) { throw 'Start this profile with -DebugServer first.' }
    & $session.java --add-modules jdk.jdi (Join-Path $script:LabRoot 'dev\tools\DebugProbe.java') $session.debugPort (Join-Path $directory 'commands')
    if ($LASTEXITCODE -ne 0) { throw 'Live debugger breakpoint verification failed.' }
    exit 0
}
$startLock = $null
try {
    if ($Action -in @('play','restart','start','run')) {
        $startLock = Enter-LabStartLock
        $exclude = if ($Action -in @('start','run')) { $directory } else { '' }
        Stop-LabOtherServers -ExcludeDirectory $exclude
    }
    if (Test-LabActive $directory) {
        if ($Action -in @('restart','play')) { throw 'A server is still holding this profile; automatic shutdown did not complete.' }
        elseif ($Action -in @('start','run')) {
            $activeSession = Read-LabJson (Join-Path $directory 'session.json')
            if ($DebugServer -and $activeSession.debugPort -ne $DebugPort) { throw 'This server has no matching debug port. Run mcdev restart -DebugServer first.' }
            Write-Host "MCDEV_READY $Version at 127.0.0.1:$($activeSession.port) (already running; use restart after edits)"
            exit 0
        }
        else { throw 'This profile is already running; stop it before running tests or preparing it.' }
    }
    if ($Action -eq 'smoke' -and -not $PluginPath -and -not $Checks) { $Checks = Join-Path $script:LabRoot 'dev\checks\project.json' }
    $session = Prepare-LabServer -Directory $directory -Version $Version -PluginPath $PluginPath -DependencyPath $DependencyPath -ServerJar $ServerJar -JavaVersion $JavaVersion -Port $Port -DebugPort $DebugPort -MemoryMb $MemoryMb -DebugServer:$DebugServer -SkipBuild:$SkipBuild -UpdateServer:$UpdateServer
    if ($Action -eq 'prepare') { exit 0 }
    if ($Action -ne 'smoke') {
        Start-LabServer $directory $session $TimeoutSeconds
        if ($Action -eq 'play') {
            Write-Host "Open Minecraft Java $Version using any launcher."
            Write-Host "Multiplayer > Direct Connection > 127.0.0.1:$($session.port)"
            Write-Host 'After edits: mcdev restart. When finished: mcdev stop.'
        }
    }
} finally {
    if ($startLock) { $startLock.ReleaseMutex(); $startLock.Dispose() }
}
if ($Action -ne 'smoke') {
    if ($Action -eq 'run') {
        Write-Host 'Use the Minecraft: Server command task to send commands; stop with Minecraft: Stop server.'
        $lineCount = 0
        while (Test-LabActive $directory) {
            $lines = @(Get-Content (Join-Path $directory 'console.log') -Encoding UTF8)
            if ($lines.Count -gt $lineCount) { $lines | Select-Object -Skip $lineCount | ForEach-Object { Write-Host $_ }; $lineCount = $lines.Count }
            Start-Sleep -Milliseconds 300
        }
    }
    exit 0
}
$reportRoot = Join-Path $script:LabRoot "build\reports\lab\$Version-$Profile"
New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
$result = @{version=$Version;profile=$Profile;passed=$false;error=$null;status=$null;plugins=$session.plugins;runId=$session.runId;checks=@();server=(Read-LabJson (Join-Path $directory 'server-pin.json'));artifacts=(Read-LabJson (Join-Path $directory 'plugins.json'))}
try {
    Start-LabServer $directory $session $TimeoutSeconds
    if (-not ('MinecraftStatus' -as [type])) { Add-Type -Path (Join-Path $script:LabRoot 'dev\tools\MinecraftStatus.cs') }
    $status = [MinecraftStatus]::Query($session.port) | ConvertFrom-Json
    if ($status.version.name -notmatch [regex]::Escape($Version)) { throw "Server status reports the wrong version: $($status.version.name)" }
    $result.status = $status
    foreach ($plugin in $session.plugins) {
        $offset = (Read-LabLog $directory).Length
        Send-LabCommand $directory "labcheck $plugin"
        Wait-LabText $directory ('MCDEV_CHECK_OK ' + [regex]::Escape($plugin) + ' ') 30 $offset
        $result.checks += "Plugin enabled: $plugin"
    }
    if ($Checks) {
        $testPlan = Read-LabJson (Resolve-Path -LiteralPath $Checks).Path
        foreach ($check in $testPlan.checks) {
            if (-not $check.command -or -not $check.expect) { throw 'Each check requires a command and expect regex.' }
            $offset = (Read-LabLog $directory).Length
            Send-LabCommand $directory $check.command
            Wait-LabText $directory $check.expect 30 $offset
            $result.checks += "Command passed: $($check.command)"
        }
    }
    Stop-LabServer $directory
    $log = Read-LabLog $directory
    $errors = @(Get-LabErrors $log)
    if ($errors.Count) { throw ("Server logged errors:`n" + ($errors -join "`n")) }
    if ($log -notmatch 'Stopping server') { throw 'Clean shutdown was not observed in the server log.' }
    $result.passed = $true
    Write-Host "PASS: $Version protocol status, enabled plugins, commands, and clean shutdown." -ForegroundColor Green
} catch {
    $result.error = $_.Exception.Message
    throw
} finally {
    if (Test-LabActive $directory) { try { Stop-LabServer $directory } catch { Write-Warning $_.Exception.Message } }
    if (Test-Path (Join-Path $directory 'console.log')) { Copy-Item (Join-Path $directory 'console.log') (Join-Path $reportRoot 'server.log') -Force }
    Write-LabJson (Join-Path $reportRoot 'result.json') $result
}
