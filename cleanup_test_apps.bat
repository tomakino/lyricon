@echo off
REM ============================================
REM 清理开发过程中残留的测试包
REM 谨慎使用！会卸载列出的应用
REM ============================================

echo ============================================
echo 以下测试包将被卸载:
echo ============================================

set APPS=com.makino.myapplication ^
com.makino.lyricon.myapplicationtest ^
com.example.application ^
com.example.application14 ^
com.example.application15 ^
com.example.applicatioap1 ^
com.example.api ^
com.example.yl ^
com.example.foxconniqdemo ^
com.app1 ^
com.app2 ^
io.github.libxposed.example ^
com.example.com.netease.cloudmusic

for %%a in (%APPS%) do (
    echo   - %%a
)

echo.
echo ============================================
set /p confirm="确认卸载以上测试包？(Y/N): "
if /i not "%confirm%"=="Y" (
    echo 已取消
    pause
    exit /b
)

echo.
echo 开始卸载...
for %%a in (%APPS%) do (
    echo 卸载 %%a ...
    adb uninstall %%a 2>nul
    if errorlevel 1 (
        echo   [跳过] 未安装或卸载失败
    ) else (
        echo   [完成]
    )
)

echo.
echo ============================================
echo 清理完成！建议重启手机以释放所有内存。
echo ============================================
pause
