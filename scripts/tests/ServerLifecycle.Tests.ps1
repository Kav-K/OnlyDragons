$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $projectRoot 'scripts\Lab.Common.ps1')
$testRoot = Join-Path $projectRoot ('build\tests\server-lifecycle-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot,(Join-Path $testRoot 'scripts'),(Join-Path $testRoot 'classes') -Force | Out-Null
Copy-Item (Join-Path $projectRoot 'scripts\Lab.Common.ps1'),(Join-Path $projectRoot 'scripts\Lab.Processes.ps1'),(Join-Path $projectRoot 'scripts\Lab.Worker.ps1') (Join-Path $testRoot 'scripts')
$jdk = Get-LabJdk 25
& (Join-Path $jdk 'bin\javac.exe') -d (Join-Path $testRoot 'classes') (Join-Path $projectRoot 'dev\tools\LifecycleFixture.java')
if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed' }
& (Join-Path $jdk 'bin\jar.exe') --create --file (Join-Path $testRoot 'fixture.jar') --main-class LifecycleFixture -C (Join-Path $testRoot 'classes') .
if ($LASTEXITCODE -ne 0) { throw 'Fixture packaging failed' }
$started = @()
$unrelated = $null
$checks = @()

function Start-Fixture([string]$Name, [bool]$IgnoreStop = $false) {
    $directory = Join-Path $testRoot "run\servers\26.2-$Name"
    New-Item -ItemType Directory -Path $directory,(Join-Path $directory 'commands') -Force | Out-Null
    Copy-Item (Join-Path $testRoot 'fixture.jar') (Join-Path $directory 'server.jar')
    $session = @{runId=[guid]::NewGuid().ToString('N');java=(Join-Path $jdk 'bin\java.exe');arguments=('-Xms16m -Xmx32m -Dfixture.ignoreStop=' + $IgnoreStop.ToString().ToLower() + ' -jar server.jar --nogui');version='26.2';port=0;debugPort=0;plugins=@()}
    Write-LabJson (Join-Path $directory 'session.json') $session
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $testRoot 'scripts\Lab.Worker.ps1') + '" -Directory "' + $directory + '"'
    Start-Process powershell.exe -ArgumentList $arguments -WindowStyle Hidden | Out-Null
    $script:started += $directory
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = Read-LabJson (Join-Path $directory 'state.json')
        if ($state -and $state.status -eq 'ready') {
            return Get-LabManagedServer $directory (Get-LabProcesses)
        }
        Start-Sleep -Milliseconds 100
    }
    throw 'Fixture failed to become ready'
}

try {
    $unrelated = [Diagnostics.Process]::new()
    $unrelated.StartInfo.FileName = Join-Path $jdk 'bin\java.exe'
    $unrelated.StartInfo.Arguments = '-Xms16m -Xmx32m -jar "' + (Join-Path $testRoot 'fixture.jar') + '" --nogui'
    $unrelated.StartInfo.UseShellExecute = $false
    $unrelated.StartInfo.CreateNoWindow = $true
    $unrelated.StartInfo.RedirectStandardInput = $true
    $unrelated.StartInfo.RedirectStandardOutput = $true
    if (-not $unrelated.Start()) { throw 'Could not create unrelated Java fixture' }
    if (@(Get-LabManagedServers | Where-Object { $_.java.ProcessId -eq $unrelated.Id }).Count) { throw 'Unrelated Java process was misidentified' }
    $checks += 'Unrelated Java processes are excluded'

    $graceful = Start-Fixture 'graceful'
    if (-not $graceful.java -or -not $graceful.worker) { throw 'Managed fixture not discovered' }
    $stateFile = Join-Path $graceful.directory 'state.json'
    $original = [IO.File]::ReadAllText($stateFile)
    $state = $original | ConvertFrom-Json
    $state.javaPid = $unrelated.Id
    Write-LabJson $stateFile $state
    $mismatch = Get-LabManagedServer $graceful.directory (Get-LabProcesses)
    if ($mismatch.java -and $mismatch.java.ProcessId -eq $unrelated.Id) { throw 'Stale PID matched unrelated Java process' }
    Write-LabText $stateFile $original
    $checks += 'Stale Java PID cannot select an unrelated process'

    Stop-LabManagedServer $graceful -Timeout 5
    $state = Read-LabJson $stateFile
    if ($state.exitCode -ne 0 -or (Read-LabLog $graceful.directory) -notmatch 'fixture saved') { throw 'Graceful shutdown failed' }
    Stop-LabManagedServer $graceful -Timeout 1
    $checks += 'Graceful shutdown saves and repeated shutdown is harmless'

    $hung = Start-Fixture 'hung' $true
    Stop-LabManagedServer $hung -Timeout 1
    $state = Read-LabJson (Join-Path $hung.directory 'state.json')
    if (-not $state.forcedStop -or (Get-LabManagedServer $hung.directory (Get-LabProcesses))) { throw 'Hung fixture was not forced closed' }
    $checks += 'Unresponsive managed server is force-stopped after timeout'

    $orphan = Start-Fixture 'orphan' $true
    Stop-LabOwnedProcess $orphan.worker
    $discovered = @(Get-LabManagedServers | Where-Object directory -eq $orphan.directory)
    if ($discovered.Count -ne 1 -or -not $discovered[0].java) { throw 'Orphaned managed Java was not discovered' }
    Stop-LabManagedServer $discovered[0] -Timeout 1
    if (Get-LabManagedServer $orphan.directory (Get-LabProcesses)) { throw 'Orphaned managed Java survived shutdown' }
    $checks += 'Tagged orphaned server is found and stopped'
    if ($unrelated.HasExited) { throw 'Unrelated Java process was terminated' }
    $checks += 'Unrelated Java survives all shutdown scenarios'
    Write-Host ('PASS: ' + ($checks -join '; '))
    Write-LabJson (Join-Path $testRoot 'result.json') @{passed=$true;checks=$checks}
} finally {
    foreach ($directory in $script:started) {
        $server = Get-LabManagedServer $directory (Get-LabProcesses)
        if ($server) { Stop-LabManagedServer $server -Timeout 1 }
    }
    if ($unrelated) {
        if (-not $unrelated.HasExited) { $unrelated.StandardInput.WriteLine('stop'); if (-not $unrelated.WaitForExit(3000)) { $unrelated.Kill() } }
        $unrelated.Dispose()
    }
}
