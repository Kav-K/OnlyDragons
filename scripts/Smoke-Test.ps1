param([int]$TimeoutSeconds=240, [switch]$SkipBuild)
# Compatibility shortcut; all runtime checks use the same lab pipeline.
& "$PSScriptRoot\Lab.ps1" smoke -TimeoutSeconds $TimeoutSeconds -SkipBuild:$SkipBuild
