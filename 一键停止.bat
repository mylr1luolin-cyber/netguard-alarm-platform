@echo off
chcp 936 >nul
setlocal
title NetGuard 一键停止

rem Stop the NetGuard application (java -jar netguard.jar ...).
rem MySQL is left running on purpose - other tools share it.
rem This file is stored as GBK + CRLF.

echo ============================================================
echo    NetGuard · 停止服务
echo ============================================================
echo.

powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*netguard.jar*' }; if ($p) { $p | ForEach-Object { Write-Host ('停止进程 PID ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force }; Write-Host '服务已停止' } else { Write-Host '没有正在运行的 NetGuard 服务' }"

echo.
if not "%NG_NOPAUSE%"=="1" pause
exit /b 0
