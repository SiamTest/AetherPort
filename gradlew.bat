@echo off
setlocal enabledelayedexpansion
set VERSION=8.11.1
set ROOT_DIR=%~dp0
set CACHE_DIR=%ROOT_DIR%.gradle-dist
set GRADLE_DIR=%CACHE_DIR%\gradle-%VERSION%
set ZIP=%CACHE_DIR%\gradle-%VERSION%-bin.zip
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  if not exist "%ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
    if errorlevel 1 exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%CACHE_DIR%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_DIR%\bin\gradle.bat" %*
exit /b %errorlevel%
