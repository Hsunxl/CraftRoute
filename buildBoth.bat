@echo off
setlocal
cd /d "%~dp0"

REM ============================================================
REM 版本号 +0.0.1 -> 构建 -> 复制进整合包 mods
REM 产物只有一个：craftroute-<ver>_neoforge_1.21.1.jar
REM 目标目录默认 <项目>/../../../mods，可用 -PmodsDir=<路径> 覆盖
REM ============================================================

echo === [1/2] Bump version +0.0.1 ===
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bump-version.ps1"
if errorlevel 1 goto :error

echo.
echo === [2/2] Build + copy to mods ===
call gradlew.bat jar copyJarToMods --no-daemon
if errorlevel 1 goto :error

echo.
echo Build OK.
goto :eof

:error
echo.
echo Build FAILED.
exit /b 1
