<#
.SYNOPSIS
Keeps the editor smoke shortcut on the shared Windows lab pipeline.
.DESCRIPTION
Delegates to Lab.ps1 smoke with the supplied startup timeout and build choice.
Uses the lab's separate default smoke profile/port, existing EULA, plugin/command
checks and cleanup; it does not start a protocol player or prove human visuals.
.PARAMETER TimeoutSeconds
Startup deadline forwarded unchanged to the lab; defaults to 240 seconds.
.PARAMETER SkipBuild
Reuse existing artifacts rather than rerun the wrapper build.
#>
param([int]$TimeoutSeconds=240, [switch]$SkipBuild)
# Compatibility shortcut; all runtime checks use the same lab pipeline.
& "$PSScriptRoot\Lab.ps1" smoke -TimeoutSeconds $TimeoutSeconds -SkipBuild:$SkipBuild
