@echo off
rem Double-click Windows smoke workflow using the separate default smoke profile.
rem Checks protocol status and console assertions then requests clean shutdown.
rem Pause retains the result for the operator. This does not run a logged-in player.
call "%~dp0mcdev.cmd" smoke
pause
