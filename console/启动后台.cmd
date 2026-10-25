@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -OpenBrowser
if errorlevel 1 pause
