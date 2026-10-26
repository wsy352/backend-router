@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
set "START_EXIT_CODE=%ERRORLEVEL%"
echo.
if not "%START_EXIT_CODE%"=="0" echo Startup failed. Read the error above.
if /I not "%~1"=="-CheckOnly" pause
exit /b %START_EXIT_CODE%
