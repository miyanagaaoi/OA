@echo off
REM ===========================================================================
REM  stop-local.cmd -- convenience entry point (same ExecutionPolicy reason as
REM  start-local.cmd).
REM  Usage: stop-local.cmd [ -OnlyApp | -OnlyServices ]
REM ===========================================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-local.ps1" %*
exit /b %ERRORLEVEL%
