@echo off
echo ============================================
echo 内存泄漏检查工具
echo ============================================

echo.
echo [1] 当前内存状态:
adb shell "cat /proc/meminfo | head -6"

echo.
echo [2] Shmem 详情:
adb shell "cat /proc/meminfo | grep -i shmem"

echo.
echo [3] Swap 状态:
adb shell "cat /proc/meminfo | grep -i swap"

echo.
echo [4] GPU 内存:
adb shell "dumpsys gpu" 2>nul | findstr "Global total"

echo.
echo [5] 前10大内存进程:
adb shell "dumpsys meminfo" 2>nul | findstr /R "^\s*[0-9].*K:" | head -10

echo.
echo ============================================
pause
