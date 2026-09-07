@echo off
rem Entry point for the human Windows lab. Arguments are forwarded to Lab.ps1.
rem Uses the script directory so invocation is independent of the caller working directory.
rem Keeps module-path changes local and returns the PowerShell exit status.
rem Play and restart may stop verified managed servers across projects.
setlocal
rem Keep Windows PowerShell modules separate from a calling PowerShell 7 session.
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Lab.ps1" %*
exit /b %errorlevel%
