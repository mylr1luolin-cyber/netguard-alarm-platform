@echo off
chcp 936 >nul
setlocal EnableExtensions
title NetGuard - find MySQL
cd /d "%~dp0.."

rem ============================================================
rem  Show every MySQL installation found on this machine.
rem  If nothing is listed, install MySQL or set NG_MYSQL_HOME.
rem ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-mysql.ps1"
set "RC=%ERRORLEVEL%"

echo.
echo ------------------------------------------------------------
echo  What this means
echo ------------------------------------------------------------
if "%RC%"=="0" (
    echo  BEST line above is the folder NetGuard will use.
    echo  start-mysql.bat / ÐÞ¸´µÇÂ¼.bat pick it automatically.
) else (
    echo  No MySQL found on this machine.
    echo  The app can show the login page but nobody can log in,
    echo  because accounts live in MySQL.
    echo.
    echo  Install:   winget install Oracle.MySQL
    echo  Portable:  copy D:\devtools\mysql\mysql-8.0.28-winx64 here
    echo  Elsewhere: set NG_MYSQL_HOME=D:\path\to\mysql
)
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b %RC%
