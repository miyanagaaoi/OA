@echo off
REM ===========================================================================
REM  start-local.cmd -- convenience entry point.
REM  The local ExecutionPolicy is Restricted (all scopes Undefined on Windows
REM  client), so invoking the .ps1 directly is refused. This wrapper starts a
REM  child PowerShell with -ExecutionPolicy Bypass.
REM  Usage: start-local.cmd [ -SkipApp | -RestartApp ]
REM ===========================================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-local.ps1" %*
exit /b %ERRORLEVEL%
