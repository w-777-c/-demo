@echo off
setlocal
cd /d "%~dp0"

set "VERSION=%~1"
if "%VERSION%"=="" set "VERSION=1.2.0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1" -Version "%VERSION%"
if errorlevel 1 (
    echo.
    echo EXE packaging failed. See the error above.
    pause
    exit /b 1
)

echo.
echo EXE packaging completed. Output is under dist\
