param([Parameter(Mandatory=$true)][string]$Directory)
. "$PSScriptRoot\Lab.Common.ps1"
$lockHandle = $null
$process = $null
$writer = $null
$started = $false
$state = @{status='starting'; workerPid=$PID; javaPid=0; exitCode=$null; error=$null}
try {
    $lockHandle = [IO.File]::Open((Join-Path $Directory 'session.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $config = Read-LabJson (Join-Path $Directory 'session.json')
    $state.runId = $config.runId
    Write-LabJson (Join-Path $Directory 'state.json') $state
    $writer = [IO.StreamWriter]::new((Join-Path $Directory 'console.log'), $false, [Text.UTF8Encoding]::new($false))
    $writer.AutoFlush = $true
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $config.java
    $directoryTag = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
    $start.Arguments = '-DminecraftPluginLab.runId=' + $config.runId + ' "-DminecraftPluginLab.directory=' + $directoryTag + '" ' + $config.arguments
    $start.WorkingDirectory = $Directory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Java process could not start.' }
    $started = $true
    $state.javaPid = $process.Id
    Write-LabJson (Join-Path $Directory 'state.json') $state
    $stdoutTask = $process.StandardOutput.ReadLineAsync()
    $stderrTask = $process.StandardError.ReadLineAsync()
    while ($true) {
        foreach ($streamName in @('stdoutTask','stderrTask')) {
            $pending = Get-Variable -Name $streamName -ValueOnly
            if ($null -eq $pending -or -not $pending.IsCompleted) { continue }
            $line = $pending.GetAwaiter().GetResult()
            if ($null -eq $line) { Set-Variable -Name $streamName -Value $null; continue }
            $line = $line -replace '\x1b\[[0-9;]*[A-Za-z]', ''
            $writer.WriteLine($line)
            if ($line -match 'Done \([0-9.,]+s\)!' -and $state.status -eq 'starting') {
                $state.status = 'ready'
                Write-LabJson (Join-Path $Directory 'state.json') $state
            }
            $reader = if ($streamName -eq 'stdoutTask') { $process.StandardOutput } else { $process.StandardError }
            Set-Variable -Name $streamName -Value ($reader.ReadLineAsync())
        }
        if (-not $process.HasExited) {
            foreach ($file in @(Get-ChildItem -LiteralPath (Join-Path $Directory 'commands') -Filter '*.cmd' -File | Sort-Object LastWriteTime)) {
                foreach ($command in [IO.File]::ReadAllLines($file.FullName)) {
                    $process.StandardInput.WriteLine($command)
                }
                $process.StandardInput.Flush()
                Remove-Item -LiteralPath $file.FullName
            }
        }
        if ($process.HasExited -and $null -eq $stdoutTask -and $null -eq $stderrTask) { break }
        Start-Sleep -Milliseconds 10
    }
    $state.exitCode = $process.ExitCode
    $state.status = if ($process.ExitCode -eq 0) {'stopped'} else {'failed'}
} catch {
    $state.status = 'failed'
    $state.error = $_.Exception.Message
    if ($writer) { $writer.WriteLine('WORKER_ERROR: ' + $_.Exception.Message) }
} finally {
    if ($started -and -not $process.HasExited) {
        try { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() } catch { }
        if (-not $process.WaitForExit(30000)) { $process.Kill(); $process.WaitForExit() }
    }
    if ($writer) { $writer.Dispose() }
    if ($process) { $process.Dispose() }
    if ($lockHandle) {
        Write-LabJson (Join-Path $Directory 'state.json') $state
        $lockHandle.Dispose()
    }
}
