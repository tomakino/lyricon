@echo off
REM ============================================
REM 一键清理 + Install Debug 脚本
REM 解决反复 install 变卡的问题
REM ============================================

echo [1/4] 清理系统缓存...
adb shell "sync && echo 3 > /proc/sys/vm/drop_caches" 2>nul

echo [2/4] 杀死后台进程...
adb shell "am kill-all" 2>nul

echo [3/4] 清理 logcat 缓冲区...
adb logcat -c

echo [4/4] 开始安装...
if "%~1"=="" (
    echo 请将 APK 文件拖拽到此脚本上，或使用: install_clean.bat your_app.apk
    pause
    exit /b
)
adb install -r -t "%~1"

echo.
echo ============================================
echo 安装完成！当前内存状态:
echo ============================================
adb shell "cat /proc/meminfo | head -5"
echo.
pause
