@echo off
title NetGuard - Network Fault Alarm Platform (dev)
rem ============================================================
rem  Windows local demo launcher
rem  Prereq: sql\netguard.sql imported into MySQL (127.0.0.1:3306/netguard)
rem  Redis / RabbitMQ optional: falls back to memory cache + local notify
rem  URL: http://localhost:8080   Accounts: admin/admin123  ops/ops123
rem ============================================================

rem --- locate a JDK automatically (env var -> registry -> PATH -> known dirs) ---
set "JAVA_HOME="
for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\find-jdk.ps1" -Quiet`) do set "JAVA_HOME=%%j"
rem fallback for this machine, in case PowerShell is blocked by policy
if not defined JAVA_HOME if exist "D:\devtools\jdk17\jdk-17.0.2\bin\java.exe" set "JAVA_HOME=D:\devtools\jdk17\jdk-17.0.2"
if not defined JAVA_HOME (
  echo [ERROR] No JDK found on this machine.
  echo         Install JDK 17:  winget install EclipseAdoptium.Temurin.17.JDK -e
  echo         Then run tools\find-jdk.ps1 to verify detection.
  pause
  exit /b 1
)
echo [INFO] JAVA_HOME=%JAVA_HOME%

if not exist "%~dp0target\netguard.jar" (
  echo [ERROR] target\netguard.jar missing. Run: mvn -B -DskipTests package
  pause
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%~dp0target\netguard.jar" --spring.profiles.active=dev
pause
