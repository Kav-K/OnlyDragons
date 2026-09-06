function Prepare-LabServer {
    param($Directory, [string]$Version, [string]$PluginPath, [string]$DependencyPath,
          [string]$ServerJar, [int]$JavaVersion, [int]$Port, [int]$DebugPort,
          [int]$MemoryMb, [switch]$DebugServer, [switch]$SkipBuild, [switch]$UpdateServer)
    if (Test-LabActive $Directory) { throw 'Stop this profile before changing its plugins or server.' }
    $pins = Get-LabPins
    $jdkBuild = Get-LabJdk ([int]$pins.javaVersion) -Install
    if (-not $JavaVersion) { $JavaVersion = Get-LabJavaVersion $Version }
    $jdkRuntime = Get-LabJdk $JavaVersion -Install
    if (-not $SkipBuild) {
        $env:JAVA_HOME = $jdkBuild
        Push-Location $script:LabRoot
        try {
            $tasks = if ($PluginPath) { @('labHarnessJar') } else { @('build','labHarnessJar') }
            & .\gradlew.bat @tasks --console=plain | ForEach-Object { Write-Host $_ }
            if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed; server was not changed.' }
        } finally { Pop-Location }
    }
    if (-not $PluginPath) {
        $artifactFile = Join-Path $script:LabRoot 'build\plugin-artifact.txt'
        if (-not (Test-Path -LiteralPath $artifactFile)) { throw 'Build the project first, or omit -SkipBuild.' }
        $PluginPath = [IO.File]::ReadAllText($artifactFile).Trim()
    }
    $jars = @(Get-LabJarPaths $PluginPath)
    if ($DependencyPath) { $jars += @(Get-LabJarPaths $DependencyPath) }
    $jars += Join-Path $script:LabRoot 'build\lab-tools\OnlyDragonsHarness.jar'
    $plugins = @($jars | Select-Object -Unique | ForEach-Object { Get-LabPlugin $_ })
    if ($plugins.Count -lt 2) { throw 'No plugin JAR was supplied.' }
    $duplicates = $plugins | Group-Object name | Where-Object Count -gt 1
    if ($duplicates) { throw "Duplicate plugin names: $($duplicates.Name -join ', ')" }
    foreach ($plugin in $plugins) {
        if ($plugin.javaVersion -gt $JavaVersion) { throw "$($plugin.name) requires Java $($plugin.javaVersion), but $Version uses Java $JavaVersion. Compile for the target server or choose a compatible plugin JAR." }
        if ($plugin.apiVersion -and [version]$plugin.apiVersion -gt [version]$Version) { throw "$($plugin.name) requires Minecraft API $($plugin.apiVersion); it cannot run on $Version. Select a compatible JAR." }
    }
    $eulaFile = Join-Path $script:LabRoot 'run\eula.txt'
    if (-not (Test-Path $eulaFile) -or -not (Select-String -LiteralPath $eulaFile -Pattern '^eula=true\s*$' -Quiet)) { throw 'Minecraft EULA acceptance is required in run/eula.txt.' }
    New-Item -ItemType Directory -Path $Directory,(Join-Path $Directory 'plugins'),(Join-Path $Directory 'commands') -Force | Out-Null
    $serverPin = Read-LabJson (Join-Path $Directory 'server-pin.json')
    if ($ServerJar) {
        $custom = (Resolve-Path -LiteralPath $ServerJar).Path
        $hash = (Get-FileHash $custom -Algorithm SHA256).Hash.ToLower()
        if ($serverPin -and $serverPin.sha256 -ne $hash -and -not $UpdateServer) { throw 'This profile has a different server JAR pinned. Use a new profile or explicitly pass -UpdateServer.' }
        $serverPin = [pscustomobject]@{kind='custom'; minecraftVersion=$Version; build='custom'; sha256=$hash; url=$null}
        Copy-Item -LiteralPath $custom -Destination (Join-Path $Directory 'server.jar') -Force
    } else {
        if (-not $serverPin -or $UpdateServer) {
            if ($Version -eq $pins.minecraftVersion -and -not $UpdateServer) {
                $serverPin = [pscustomobject]@{kind='paper';minecraftVersion=$Version;build=$pins.paperBuild;sha256=$pins.paperSha256;url=$pins.paperUrl}
            } else {
                $release = Get-LabPaperBuild $Version
                $download = $release.downloads.'server:default'
                $serverPin = [pscustomobject]@{kind='paper';minecraftVersion=$Version;build=$release.id;sha256=$download.checksums.sha256;url=$download.url}
            }
        }
        $jar = Join-Path $Directory 'server.jar'
        if (-not (Test-Path $jar) -or (Get-FileHash $jar -Algorithm SHA256).Hash -ne $serverPin.sha256) {
            if (-not $serverPin.url) { throw 'The custom server JAR is missing or modified; supply -ServerJar again.' }
            $cache = Join-Path $script:LabRoot 'run\downloads'
            New-Item -ItemType Directory -Path $cache -Force | Out-Null
            $cached = Join-Path $cache ($serverPin.sha256 + '.jar')
            $original = Join-Path $script:LabRoot 'run\paper.jar'
            if ((Test-Path $original) -and (Get-FileHash $original -Algorithm SHA256).Hash -eq $serverPin.sha256) { Copy-Item $original $cached -Force }
            if (-not (Test-Path $cached) -or (Get-FileHash $cached -Algorithm SHA256).Hash -ne $serverPin.sha256) {
                Write-Host "Downloading pinned Paper $Version build $($serverPin.build)..."
                $ProgressPreference = 'SilentlyContinue'
                Invoke-WebRequest $serverPin.url -OutFile "$cached.download" -UserAgent $script:LabAgent -UseBasicParsing -TimeoutSec 300
                if ((Get-FileHash "$cached.download" -Algorithm SHA256).Hash -ne $serverPin.sha256) { throw 'Paper checksum mismatch.' }
                Move-Item -LiteralPath "$cached.download" -Destination $cached -Force
            }
            Copy-Item -LiteralPath $cached -Destination $jar -Force
        }
    }
    Write-LabJson (Join-Path $Directory 'server-pin.json') $serverPin
    $oldPlugins = Read-LabJson (Join-Path $Directory 'plugins.json')
    $pluginDirectory = Join-Path $Directory 'plugins'
    foreach ($existing in @(Get-ChildItem -LiteralPath $pluginDirectory -Filter '*.jar' -File)) {
        $old = $oldPlugins | Where-Object { ($_.name + '.jar') -eq $existing.Name } | Select-Object -First 1
        if (-not $old -or (Get-FileHash $existing.FullName -Algorithm SHA256).Hash -ne $old.sha256) { throw "Unmanaged or modified plugin JAR: $($existing.FullName). Select a fresh -Profile or preserve it outside plugins and supply it through -DependencyPath." }
    }
    foreach ($old in $oldPlugins) {
        if ($old -and $old.name -notin $plugins.name) {
            $stale = Join-Path $pluginDirectory ($old.name + '.jar')
            if (Test-Path -LiteralPath $stale) { Remove-Item -LiteralPath $stale }
        }
    }
    foreach ($plugin in $plugins) { Copy-Item -LiteralPath $plugin.path -Destination (Join-Path $pluginDirectory ($plugin.name + '.jar')) -Force }
    Write-LabJson (Join-Path $Directory 'plugins.json') $plugins
    $propertiesPath = Join-Path $Directory 'server.properties'
    if (-not (Test-Path $propertiesPath)) { Copy-Item (Join-Path $script:LabRoot 'dev\server.properties') $propertiesPath }
    $properties = [IO.File]::ReadAllText($propertiesPath)
    foreach ($setting in @{ 'server-ip'='127.0.0.1'; 'server-port'=[string]$Port; 'online-mode'='true'; 'enable-rcon'='false'; 'enable-query'='false' }.GetEnumerator()) {
        $pattern = '(?m)^' + [regex]::Escape($setting.Key) + '=.*$'
        if ($properties -match $pattern) { $properties = [regex]::Replace($properties,$pattern,($setting.Key + '=' + $setting.Value)) }
        else { $properties += "`n$($setting.Key)=$($setting.Value)" }
    }
    Write-LabText $propertiesPath $properties
    Copy-Item -LiteralPath $eulaFile -Destination (Join-Path $Directory 'eula.txt') -Force
    $javaArgs = "-Xms256m -Xmx${MemoryMb}m -Dfile.encoding=UTF-8 -Dterminal.jline=false -Dterminal.ansi=false -Doshi.os.windows.hkeyperfdata=false"
    if ($DebugServer) { $javaArgs += " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:$DebugPort" }
    $javaArgs += ' -jar server.jar --nogui'
    $session = [pscustomobject]@{runId=[guid]::NewGuid().ToString('N'); version=$Version; javaVersion=$JavaVersion; java=(Join-Path $jdkRuntime 'bin\java.exe'); arguments=$javaArgs; port=$Port; debugPort=$(if ($DebugServer) {$DebugPort} else {0}); plugins=@($plugins.name)}
    Write-LabJson (Join-Path $Directory 'session.json') $session
    Write-Host "Prepared $Version with $($plugins.name -join ', ') in $Directory"
    return $session
}

function Start-LabServer($Directory, $Session, [int]$Timeout) {
    Assert-LabPort $Session.port
    if ($Session.debugPort) { Assert-LabPort $Session.debugPort }
    # Remove only stale commands owned by this stopped profile.
    Get-ChildItem -LiteralPath (Join-Path $Directory 'commands') -Filter '*.cmd' -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    $worker = Join-Path $PSScriptRoot 'Lab.Worker.ps1'
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $worker + '" -Directory "' + $Directory + '"'
    Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WindowStyle Hidden | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds($Timeout)
    while ($true) {
        $state = Read-LabJson (Join-Path $Directory 'state.json')
        if ($state -and $state.runId -eq $Session.runId) {
            if ($state.status -eq 'ready') { break }
            if ($state.status -in @('failed','stopped')) { throw "Server failed to start: $($state.error). Inspect $Directory\console.log" }
        }
        if ([DateTime]::UtcNow -gt $deadline) { try { Stop-LabServer $Directory } catch { }; throw "Server startup timed out. Inspect $Directory\console.log" }
        Start-Sleep -Milliseconds 250
    }
    Write-Host "MCDEV_READY $($Session.version) at 127.0.0.1:$($Session.port)"
}
