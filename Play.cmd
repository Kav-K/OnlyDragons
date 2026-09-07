@echo off
rem Double-click human workflow. Builds and starts the default authenticated loopback profile.
rem Delegates managed server shutdown and ownership checks to mcdev play.
rem Pause keeps connection instructions visible. It does not stop the detached server.
call "%~dp0mcdev.cmd" play
pause
