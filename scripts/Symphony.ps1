param(
    [ValidateSet('install','check','login','set-token','start','stop','status')]
    [string]$Action = 'check',
    [string]$Distribution = 'Ubuntu-24.04'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stateRoot = Join-Path $projectRoot '.symphony'
New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null
if ($Action -eq 'set-token') {
    $secureToken = Read-Host 'GitHub fine-grained token for Kav-K/OnlyDragons (hidden)' -AsSecureString
    $tokenPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    try {
        $tokenValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPointer).Trim()
        if ([string]::IsNullOrWhiteSpace($tokenValue) -or $tokenValue.Contains("`n")) { throw 'Enter one nonempty token.' }
        $tokenFile = Join-Path $stateRoot 'github-token'
        [IO.File]::WriteAllText($tokenFile, $tokenValue, (New-Object Text.UTF8Encoding($false)))
        $userIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe $tokenFile /inheritance:r /grant:r "${userIdentity}:(F)" '*S-1-5-18:(F)' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the token file permissions.' }
        Write-Host 'Token saved locally in the Git-ignored .symphony directory.'
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPointer)
        $tokenValue = $null
        $secureToken.Dispose()
    }
    exit 0
}
# Reuse the existing Codex login without displaying credentials or copying machine-specific settings.
if ($Action -eq 'login') {
    $existingCodexRoot = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $env:USERPROFILE '.codex' }
    $existingAuth = Join-Path $existingCodexRoot 'auth.json'
    if (Test-Path -LiteralPath $existingAuth) {
        $localCodexRoot = Join-Path $stateRoot 'codex'
        New-Item -ItemType Directory -Force -Path $localCodexRoot | Out-Null
        $localAuth = Join-Path $localCodexRoot 'auth.json'
        Copy-Item -LiteralPath $existingAuth -Destination $localAuth
        $userIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe $localAuth /inheritance:r /grant:r "${userIdentity}:(F)" '*S-1-5-18:(F)' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the Codex authentication file permissions.' }
        Write-Host 'Existing Codex sign-in copied to the isolated Symphony runtime.'
    }
}
$portableRoot = $projectRoot.Replace('\', '/')
$linuxRoot = (& wsl.exe -d $Distribution -- wslpath -u $portableRoot | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or -not $linuxRoot.StartsWith('/')) { throw 'Could not access the configured WSL distribution.' }
& wsl.exe -d $Distribution -- bash "$linuxRoot/scripts/symphony/run.sh" $Action
exit $LASTEXITCODE
