@echo off
rem Operator bridge to the configured project WSL Symphony runtime.
rem Forwards arguments to the PowerShell script anchored at this entry point.
rem Credential prompts and private state handling stay in the delegated script.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Symphony.ps1" %*
