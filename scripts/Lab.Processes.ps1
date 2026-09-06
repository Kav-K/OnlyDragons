# Process ownership is checked against a managed profile, its worker, and its run ID.
# Never terminate by executable name or port number alone.
function Get-LabProcesses {
    @(Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe' OR Name = 'pwsh.exe' OR Name = 'java.exe' OR Name = 'javaw.exe'")
}

function Get-LabManagedServer([string]$Directory, $Processes) {
    try {
        $full = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
        if ($full -notmatch '^(?<root>.+)\\run\\servers\\\d+(?:\.\d+)+-[A-Za-z0-9_-]+$') { return }
        $projectRoot = $matches.root
        $workerScript = Join-Path $projectRoot 'scripts\Lab.Worker.ps1'
        if (-not (Test-Path -LiteralPath $workerScript)) { return }
        $state = Read-LabJson (Join-Path $full 'state.json')
        $session = Read-LabJson (Join-Path $full 'session.json')
        if (-not $state -or -not $session -or $state.runId -ne $session.runId -or $session.runId -notmatch '^[a-f0-9]{32}$') { return }
        $worker = $Processes | Where-Object {
            $_.ProcessId -eq $state.workerPid -and $_.Name -in @('powershell.exe','pwsh.exe') -and
            $_.CommandLine -match ('(?i)(?:^|\s)-File\s+"' + [regex]::Escape($workerScript) + '"\s+-Directory\s+"' + [regex]::Escape($full) + '"\s*$')
        } | Select-Object -First 1
        $java = $Processes | Where-Object {
            $_.ProcessId -eq $state.javaPid -and $_.Name -in @('java.exe','javaw.exe') -and
            $_.ExecutablePath -eq $session.java -and
            $_.CommandLine -match '(?i)(?:^|\s)-jar\s+"?server\.jar"?\s+--nogui(?:\s|$)' -and
            (
                ($worker -and $_.ParentProcessId -eq $worker.ProcessId -and $_.CreationDate -ge $worker.CreationDate) -or
                ($_.CommandLine.Contains('-DminecraftPluginLab.runId=' + $session.runId + ' ') -and
                 $_.CommandLine.Contains('"-DminecraftPluginLab.directory=' + $full + '"'))
            )
        } | Select-Object -First 1
        if ($worker -or $java) {
            [pscustomobject]@{directory=$full;project=$projectRoot;runId=$session.runId;worker=$worker;java=$java}
        }
    } catch { Write-Warning "Could not inspect managed profile ${Directory}: $($_.Exception.Message)" }
}

function Get-LabManagedServers {
    $processes = Get-LabProcesses
    $directories = foreach ($process in $processes) {
        if ($process.Name -in @('powershell.exe','pwsh.exe') -and
            $process.CommandLine -match '(?i)(?:^|\s)-File\s+"[^"\r\n]+\\scripts\\Lab\.Worker\.ps1"\s+-Directory\s+"(?<directory>[^"\r\n]+)"\s*$') {
            $matches.directory
        }
        elseif ($process.Name -in @('java.exe','javaw.exe') -and
            $process.CommandLine -match '"-DminecraftPluginLab\.directory=(?<directory>[^"\r\n]+)"') {
            $matches.directory
        }
    }
    foreach ($directory in ($directories | Sort-Object -Unique)) {
        Get-LabManagedServer $directory $processes
    }
}

function Stop-LabOwnedProcess($Expected) {
    if (-not $Expected) { return }
    $live = Get-Process -Id $Expected.ProcessId -ErrorAction SilentlyContinue
    if (-not $live) { return }
    try {
        # Hold a process handle and check creation time so stale/reused PIDs are not killed.
        $null = $live.Handle
        if ([Math]::Abs(($live.StartTime.ToUniversalTime() - $Expected.CreationDate.ToUniversalTime()).TotalMilliseconds) -gt 1) {
            throw "Process $($Expected.ProcessId) has been replaced; refusing to terminate it."
        }
        $live.Kill()
        if (-not $live.WaitForExit(10000)) { throw "Managed process $($Expected.ProcessId) did not exit." }
    } finally { $live.Dispose() }
}

function Stop-LabManagedServer($Server, [int]$Timeout = 45) {
    $current = Get-LabManagedServer $Server.directory (Get-LabProcesses)
    if (-not $current) { return }
    if ($current.runId -ne $Server.runId) { throw 'The server session changed during shutdown. Retry Play.' }
    Write-Host "Stopping existing development server: $($current.directory)"
    if (Test-LabActive $current.directory) {
        try {
            Stop-LabServer $current.directory $Timeout
            # A graceful worker shutdown should also remove its owned Java process.
            if (-not (Get-LabManagedServer $current.directory (Get-LabProcesses))) { return }
        } catch { Write-Warning $_.Exception.Message }
    }
    $current = Get-LabManagedServer $Server.directory (Get-LabProcesses)
    if (-not $current) { return }
    if ($current.runId -ne $Server.runId) { throw 'The server session changed before forced shutdown. Retry Play.' }
    Write-Warning 'Forcing the unresponsive managed server to exit after attempting a clean shutdown.'
    Stop-LabOwnedProcess $current.java
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    while ((Test-LabActive $current.directory) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    Stop-LabOwnedProcess $current.worker
    if (Test-LabActive $current.directory) { throw 'The managed server profile is still locked.' }
    $state = Read-LabJson (Join-Path $current.directory 'state.json')
    if ($state.runId -eq $current.runId) {
        $state | Add-Member -NotePropertyName forcedStop -NotePropertyValue $true -Force
        $state.status = 'stopped'
        Write-LabJson (Join-Path $current.directory 'state.json') $state
    }
    Write-Host 'Managed server stopped (forced).'
}

function Stop-LabOtherServers([string]$ExcludeDirectory = '') {
    foreach ($server in @(Get-LabManagedServers)) {
        if ($server.directory -ne $ExcludeDirectory) { Stop-LabManagedServer $server }
    }
}

function Enter-LabStartLock {
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $mutex = [Threading.Mutex]::new($false, ('Local\MinecraftPluginLab.Start.' + $sid))
    try {
        try { $acquired = $mutex.WaitOne(30000) } catch [Threading.AbandonedMutexException] { $acquired = $true }
        if (-not $acquired) { throw 'Another Minecraft project is starting a server. Wait for it to finish, then retry.' }
        return $mutex
    } catch { $mutex.Dispose(); throw }
}
