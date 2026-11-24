@echo off
chcp 936 >nul
setlocal EnableExtensions EnableDelayedExpansion
title NetGuard 一键启动
cd /d "%~dp0"

rem ============================================================
rem  NetGuard one-click launcher (portable)
rem  Usage:  一键启动.bat          build only when sources changed
rem          一键启动.bat build    force rebuild first
rem  Portable: JDK is auto-detected; Maven / MySQL are auto-detected
rem            too, and a machine without them still works as long as
rem            target\netguard.jar exists and MySQL is already running.
rem  Overrides (optional): NG_JAVA_HOME  NG_MVN_HOME  NG_MYSQL_HOME
rem  Notes:   keep rem/comment lines ASCII; this file is stored as
rem           GBK + CRLF, matching the default console code page.
rem ============================================================

rem --- 0. JDK: override -> auto detect -> this machine's known path ---
set "JAVA_HOME="
set "JAVA="
if defined NG_JAVA_HOME if exist "%NG_JAVA_HOME%\bin\java.exe" set "JAVA_HOME=%NG_JAVA_HOME%"
if not defined JAVA_HOME for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\find-jdk.ps1" -Quiet`) do set "JAVA_HOME=%%j"
if not defined JAVA_HOME if exist "D:\devtools\jdk17\jdk-17.0.2\bin\java.exe" set "JAVA_HOME=D:\devtools\jdk17\jdk-17.0.2"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
if defined JAVA_HOME echo [0/3] 使用 JDK：%JAVA_HOME%

rem --- 1. Maven: fixed dir -> PATH (may be absent; then we reuse the jar) ---
set "MVN="
set "MVN_HOME=D:\devtools\maven\apache-maven-3.9.9"
if defined NG_MVN_HOME set "MVN_HOME=%NG_MVN_HOME%"
if exist "%MVN_HOME%\bin\mvn.cmd" set "MVN=%MVN_HOME%\bin\mvn.cmd"
if not defined MVN for /f "delims=" %%m in ('where mvn.cmd 2^>nul') do set "MVN=%%m"

rem --- 2. MySQL: fixed dir -> PATH (may be absent; then reuse a running one) ---
set "MYSQL_HOME="
set "MYSQLD="
if defined NG_MYSQL_HOME if exist "%NG_MYSQL_HOME%\bin\mysqld.exe" set "MYSQL_HOME=%NG_MYSQL_HOME%"
if not defined MYSQL_HOME for /f "usebackq delims=" %%m in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\find-mysql.ps1" -Quiet 2^>nul`) do set "MYSQL_HOME=%%m"
if defined MYSQL_HOME if exist "%MYSQL_HOME%\bin\mysqld.exe" set "MYSQLD=%MYSQL_HOME%\bin\mysqld.exe"
if not defined MYSQLD for /f "delims=" %%m in ('where mysqld 2^>nul') do set "MYSQLD=%%m"
set "JAR=%~dp0target\netguard.jar"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8"
set "APP_URL=http://localhost:8080"

echo ============================================================
echo    NetGuard 网络故障告警监控平台 · 一键启动
echo    Launcher v2 2026-11-24  (JDK/Maven/MySQL 自动识别)
echo ============================================================
echo.

if not exist "%JAVA%" goto :nojava

set "CODE=0"
for /f "usebackq delims=" %%c in (`powershell -NoProfile -Command "try{(Invoke-WebRequest '%APP_URL%/login' -UseBasicParsing -TimeoutSec 2).StatusCode}catch{0}"`) do set "CODE=%%c"
if "%CODE%"=="200" goto :already

set "DB=0"
for /f "usebackq delims=" %%c in (`powershell -NoProfile -Command "if(Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue){1}else{0}"`) do set "DB=%%c"
if "%DB%"=="1" (
    echo [1/3] MySQL 已在运行，跳过
    goto :mysqlready
)
if not defined MYSQLD goto :nomysql
echo [1/3] 正在启动 MySQL ...
powershell -NoProfile -Command "Start-Process -FilePath '%MYSQLD%' -ArgumentList '--defaults-file=%MYSQL_HOME%\my.ini' -WindowStyle Hidden"
call :waitdb
if errorlevel 1 goto :dbfail
echo       MySQL 已就绪
goto :mysqlready
:mysqlready

set "NEED="
if not exist "%JAR%" set "NEED=1"
if /i "%~1"=="build" set "NEED=1"
if not defined NEED (
    powershell -NoProfile -Command "$j=Get-Item 'target\netguard.jar'; $n=(Get-ChildItem -Recurse -File 'src','pom.xml' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime; if($n -gt $j.LastWriteTime){exit 1}else{exit 0}"
    if errorlevel 1 set "NEED=1"
)
if defined NEED if not defined MVN (
    if exist "%JAR%" (
        echo [2/3] 没找到 Maven，跳过编译，使用现有 jar（要重新编译请装 Maven 或设 NG_MVN_HOME）
        goto :skipbuild
    )
    goto :nomvn
)
if defined NEED (
    echo [2/3] 正在构建，首次启动或代码有更新时较慢 ...
    call "%MVN%" -B -q -DskipTests package
    if errorlevel 1 goto :buildfail
    echo       构建完成
) else (
    echo [2/3] 使用已有构建产物，跳过编译
)
:skipbuild

echo [3/3] 正在启动服务 ...
start "NetGuard 服务 - 关闭此窗口即停止" /D "%~dp0" "%JAVA%" -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=dev

set /a tried=0
:waitapp
set /a tried+=1
set "CODE=0"
for /f "usebackq delims=" %%c in (`powershell -NoProfile -Command "try{(Invoke-WebRequest '%APP_URL%/login' -UseBasicParsing -TimeoutSec 2).StatusCode}catch{0}"`) do set "CODE=%%c"
if "%CODE%"=="200" goto :ready
if !tried! GEQ 45 goto :timeout
ping -n 2 127.0.0.1 >nul
goto :waitapp

:ready
if not "%NG_NOBROWSER%"=="1" start "" "%APP_URL%"
echo.
echo ============================================================
echo    启动成功
echo    地址：%APP_URL%
echo    管理员：admin / admin123      运维：ops / ops123
echo    停止：关闭 "NetGuard 服务" 窗口，或双击 一键停止.bat
echo ============================================================
echo.
echo    日志在 "NetGuard 服务" 窗口里，出问题先看它
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 0

:already
echo 服务已经在运行，直接打开页面。
if not "%NG_NOBROWSER%"=="1" start "" "%APP_URL%"
if not "%NG_NOPAUSE%"=="1" pause
exit /b 0

:waitdb
set /a dbTried=0
:dbwait
set /a dbTried+=1
set "DBN=0"
for /f "usebackq delims=" %%c in (`powershell -NoProfile -Command "if(Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet -WarningAction SilentlyContinue){1}else{0}"`) do set "DBN=%%c"
if "%DBN%"=="1" exit /b 0
if !dbTried! GEQ 30 exit /b 1
ping -n 2 127.0.0.1 >nul
goto :dbwait

:timeout
echo.
echo [警告] 90 秒内服务没有起来，请看 "NetGuard 服务" 窗口的报错。常见原因：
echo          - 8080 被别的程序占用（先双击 一键停止.bat）
echo          - MySQL 没起来，或本机没有 MySQL 且 3306 不通
echo          - jar 不是最新的（运行：一键启动.bat build）
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:buildfail
echo.
echo [错误] 构建失败，请看上面的 Maven 输出。
echo        常见原因：Maven 路径变了，或代码有编译错误。
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:nojava
echo [错误] 本机没找到可用的 JDK（17 及以上）
echo        自动搜索顺序：JAVA_HOME 环境变量 -^> 注册表 -^> PATH -^> 常见安装目录 -^> 全盘
echo        安装：winget install EclipseAdoptium.Temurin.17.JDK -e
echo        或双击运行 tools\查找JDK.bat 查看本机已装的 JDK
echo        也可以先 set NG_JAVA_HOME=C:\path\to\jdk 再运行本脚本
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:nomvn
echo [错误] 需要编译，但本机既没有 Maven 也没有 target\netguard.jar
echo        方案一：装 Maven 3.9（或把 Maven 目录设成 NG_MVN_HOME）
echo        方案二：把本机编译好的 target\netguard.jar 一起拷过来，本脚本会跳过编译
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:nomysql
echo [错误] 本机找不到 MySQL（%MYSQL_HOME%\bin\mysqld.exe），且 3306 端口没有服务在监听
echo        方案一：先把 MySQL 启动起来（本机安装版 / 另一台机器都可以，只要 127.0.0.1:3306 能连）
echo        方案二：把 MySQL 解压版路径设成 NG_MYSQL_HOME，或改本文件开头的 MYSQL_HOME
echo        方案三：双击 tools\查找MySQL.bat 看本机到底有哪些 MySQL，再按提示装或指路径
echo        连接参数在 src\main\resources\application-dev.yml（默认 root/123456，库 netguard）
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1

:dbfail
echo.
echo [错误] MySQL 60 秒内没有就绪，请确认 3306 已监听、账号密码正确。
echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 1