@echo off
setlocal
rem Keep Windows PowerShell modules separate from a calling PowerShell 7 session.
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Lab.ps1" %*
exit /b %errorlevel%
