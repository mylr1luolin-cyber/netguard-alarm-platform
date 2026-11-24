@echo off
chcp 936 >nul
setlocal
title NetGuard - JDK 检查
cd /d "%~dp0.."

echo ============================================================
echo    NetGuard JDK 自动检查
echo ============================================================
echo.
echo 正在搜索本机的 JDK（环境变量 / 注册表 / PATH / 常见目录 / 全盘）...
echo 搜索全盘时可能需要几十秒，请稍等。
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk.ps1"

echo.
echo ------------------------------------------------------------
echo 说明：
echo   - 上面列出 JDK 17 就说明可用，一键启动.bat / start-dev.bat
echo     会自动找到它，不需要你手动改 JAVA_HOME。
echo   - 如果提示 No working JDK found，请先安装 JDK 17：
echo       winget install EclipseAdoptium.Temurin.17.JDK -e
echo     或到 https://adoptium.net/temurin/releases/?version=17 下载
echo     安装后重新打开本窗口再检查一次（PATH 需要刷新）。
echo ------------------------------------------------------------
echo.

if not "%NG_NOPAUSE%"=="1" pause
endlocal
