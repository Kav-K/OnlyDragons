@echo off
rem Requests graceful shutdown of the default human profile through the lab.
rem Pauses only on failure so diagnostics remain visible. No process is selected here.
call "%~dp0mcdev.cmd" stop
if errorlevel 1 pause
