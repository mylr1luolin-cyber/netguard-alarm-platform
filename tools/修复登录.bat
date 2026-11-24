@echo off
chcp 936 >nul
setlocal EnableExtensions
title NetGuard - fix login (reset admin password)
cd /d "%~dp0.."

rem ============================================================
rem  Fix "account or password incorrect" on a fresh machine.
rem  Runs sql\维护-重置管理员密码.sql through stdin redirection, so the
rem  BCrypt hash is NOT mangled by shell $ substitution.
rem  Usage: double-click, or:  tools\修复登录.bat  [mysql-root-password]
rem  Note: never put brackets inside echo lines that live in an
rem  if-block, cmd closes the block at the first ")".
rem ============================================================

set "MYSQL_HOME="
if defined NG_MYSQL_HOME set "MYSQL_HOME=%NG_MYSQL_HOME%"

if not defined MYSQL_HOME (
    for /f "usebackq delims=" %%m in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-mysql.ps1" -Quiet 2^>nul`) do set "MYSQL_HOME=%%m"
)

set "MYSQL="
if defined MYSQL_HOME if exist "%MYSQL_HOME%\bin\mysql.exe" set "MYSQL=%MYSQL_HOME%\bin\mysql.exe"
if not defined MYSQL for /f "delims=" %%m in ('where mysql.exe 2^>nul') do set "MYSQL=%%m"

if not defined MYSQL goto :nomysql

set "SQLFILE=%~dp0..\sql\维护-重置管理员密码.sql"
if not exist "%SQLFILE%" goto :nosql

set "DBPASS=%~1"
if not defined DBPASS set "DBPASS=123456"

echo.
echo Using: %MYSQL%
echo SQL  : %SQLFILE%
echo.

set "LOG=%TEMP%\netguard-fix-login.log"
"%MYSQL%" -u root -p%DBPASS% --default-character-set=utf8mb4 < "%SQLFILE%" > "%LOG%" 2>&1
type "%LOG%"

findstr /I /C:"ERROR 1045" /C:"ERROR 2003" /C:"ERROR 1049" /C:"ERROR 1044" /C:"ERROR 2005" "%LOG%" >nul 2>&1
if not errorlevel 1 goto :dbfail

echo.
echo [OK] Password reset to default: admin/admin123, ops/ops123
echo      Restart the app if it is running, then log in again.
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 0

:dbfail
echo.
echo [ERROR] MySQL refused the job. Code 1045 means wrong root password,
echo         2003 means MySQL is not running - run start-mysql.bat first,
echo         1049 means database netguard is missing - import sql\netguard.sql.
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:nosql
echo [ERROR] SQL file missing: %SQLFILE%
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:nomysql
echo.
echo [ERROR] This machine has no MySQL, so there is no account to reset.
echo.
echo   Why login fails without MySQL:
echo     1. the app reads accounts from MySQL on every login attempt
echo     2. no MySQL means no sys_user table, so every password looks wrong
echo.
echo   Do one of these first:
echo     - install MySQL 8:      winget install Oracle.MySQL
echo     - copy portable MySQL:  D:\devtools\mysql\mysql-8.0.28-winx64
echo     - point the script:     set NG_MYSQL_HOME=D:\path\to\mysql
echo.
echo   Then run start-mysql.bat, import sql\netguard.sql, and retry this.
echo   Tip: double-click tools\查找MySQL.bat to see what was found.
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1
