@echo off
chcp 936 >nul
setlocal EnableExtensions
title NetGuard - start MySQL (portable 8.0.28)
cd /d "%~dp0"

rem ============================================================
rem  Locate MySQL on this machine, then start mysqld if it is not
rem  running yet, and wait until port 3306 answers.
rem  Order: NG_MYSQL_HOME -> tools\find-mysql.ps1 -> where mysqld
rem  Windows is always stopped with a message (never just closes),
rem  so you can read what is wrong.
rem ============================================================

set "MYSQL_HOME="
if defined NG_MYSQL_HOME set "MYSQL_HOME=%NG_MYSQL_HOME%"

if not defined MYSQL_HOME (
    for /f "usebackq delims=" %%m in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\find-mysql.ps1" -Quiet 2^>nul`) do set "MYSQL_HOME=%%m"
)

if not defined MYSQL_HOME for /f "delims=" %%m in ('where mysqld.exe 2^>nul') do (
    set "MYSQL_EXE=%%m"
)
if defined MYSQL_EXE set "MYSQL_HOME=%MYSQL_EXE:\bin\mysqld.exe=%"

if not defined MYSQL_HOME goto :nomysql
if not exist "%MYSQL_HOME%\bin\mysqld.exe" goto :nomysql

echo Using MySQL: %MYSQL_HOME%

rem ---- already running? ----
powershell -NoProfile -Command "if (Test-NetConnection 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue -InformationLevel Quiet) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
    echo MySQL is already running on port 3306, nothing to do.
    if not "%NG_NOPAUSE%"=="1" if "%NG_NOPAUSE%"=="" pause
    exit /b 0
)

set "MYINI=%MYSQL_HOME%\my.ini"
if not exist "%MYINI%" set "MYINI=%MYSQL_HOME%\my-default.ini"

if exist "%MYINI%" (
    echo Starting mysqld with %MYINI% ...
    powershell -NoProfile -Command "Start-Process -FilePath '%MYSQL_HOME%\bin\mysqld.exe' -ArgumentList '--defaults-file=%MYINI%' -WindowStyle Hidden"
) else (
    echo No my.ini found, starting mysqld with default datadir ...
    powershell -NoProfile -Command "Start-Process -FilePath '%MYSQL_HOME%\bin\mysqld.exe' -WindowStyle Hidden"
)

echo Waiting for port 3306 ...
for /L %%i in (1,1,30) do (
    ping -n 2 127.0.0.1 >nul
    powershell -NoProfile -Command "if (Test-NetConnection 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue -InformationLevel Quiet) { exit 0 } else { exit 1 }"
    if not errorlevel 1 (
        echo [OK] MySQL is ready on 127.0.0.1:3306
        if not "%NG_NOPAUSE%"=="1" if "%NG_NOPAUSE%"=="" pause
        exit /b 0
    )
)

echo [ERROR] MySQL did not answer on port 3306 within 60 seconds.
echo         Look for the error log in "%MYSQL_HOME%\data\*.err"
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:nomysql
echo.
echo [ERROR] This machine has no MySQL server.
echo.
echo   The app needs MySQL to store devices and accounts.
echo   Login will fail no matter what until MySQL exists.
echo.
echo   Option 1 - install MySQL 8:
echo     winget install Oracle.MySQL
echo   Option 2 - copy the portable folder here:
echo     D:\devtools\mysql\mysql-8.0.28-winx64   (whole folder, includes bin\)
echo   Option 3 - MySQL lives elsewhere:
echo     set NG_MYSQL_HOME=D:\some\mysql\mysql-8.0.28-winx64
echo     then run this script again.
echo.
echo   Tip: double-click tools\≤È’“MySQL.bat to see what was found.
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1
