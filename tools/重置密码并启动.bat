@echo off
chcp 936 >nul
setlocal EnableExtensions
title NetGuard - reset admin password and start
cd /d "%~dp0.."

rem ============================================================
rem  Forgot / broken admin password? Run this instead of the app.
rem  It starts NetGuard with --app.reset-admin=true, which resets
rem  admin -> admin123 and ops -> ops123 at boot, then serves 8080.
rem  No MySQL client needed.
rem ============================================================

set "NG_NOBROWSER=1"
set "JAVA_HOME="
for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk.ps1" -Quiet`) do set "JAVA_HOME=%%j"

if not defined JAVA_HOME (
    if exist "D:\devtools\jdk17\jdk-17.0.2\bin\java.exe" set "JAVA_HOME=D:\devtools\jdk17\jdk-17.0.2"
)
if not defined JAVA_HOME (
    echo [ERROR] JDK not found. Double-click tools\≤È’“JDK.bat to see what is installed.
    if not "%NG_NOPAUSE%"=="1" pause
    exit /b 1
)

set "JAR=%~dp0..\target\netguard.jar"
if not exist "%JAR%" (
    echo [ERROR] jar missing: %JAR%
    echo         Run “ªº¸∆Ù∂Ø.bat once to build it first.
    if not "%NG_NOPAUSE%"=="1" pause
    exit /b 1
)

echo.
echo   Starting NetGuard with --app.reset-admin=true
echo   admin will be reset to admin123, ops to ops123
echo.
echo   Keep THIS window open. Log in at http://localhost:8080
echo   Close this window (or Ctrl+C) when you are done.
echo.
"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=dev --app.reset-admin=true
if not "%NG_NOPAUSE%"=="1" pause
exit /b 0