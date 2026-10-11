@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

echo ==========================================
echo   真正的按住疾跑  --  编译脚本
echo ==========================================
echo.

rem ---- 1. 检查 Java ----
where java >nul 2>nul
if errorlevel 1 (
    echo [X] 没检测到 Java。
    echo     请先安装 JDK 25：
    echo     https://adoptium.net/temurin/releases/?version=25
    echo     装完后重新双击本文件。
    echo.
    pause
    exit /b 1
)
echo [1/3] Java 版本：
java -version 2>&1
echo.

rem ---- 2. 检查 gradle ----
where gradle >nul 2>nul
if errorlevel 1 (
    echo [X] 没检测到 gradle 命令。
    echo     说明你的 gradle 没加入 PATH 环境变量。
    echo     请改用 README 里的方法，或先执行：
    echo     set PATH=你的gradle目录\bin;%%PATH%%
    echo.
    pause
    exit /b 1
)
echo [2/3] Gradle 版本：
gradle --version 2>nul | findstr /i "^Gradle"
echo.

rem ---- 3. 编译 ----
echo [3/3] 开始编译（第一次要下载 Minecraft 26.3，5~15 分钟，请耐心等）
echo.
gradle build --no-daemon

echo.
if errorlevel 1 (
    echo ==========================================
    echo   编译失败了。请把上面红色的报错截图发给我。
    echo ==========================================
) else (
    echo ==========================================
    echo   编译成功！jar 文件在这里：
    echo   %CD%\build\libs\
    dir /b build\libs\*.jar 2>nul
    echo.
    echo   把它复制到 .minecraft\mods\ 文件夹里就行了。
    echo ==========================================
)
echo.
pause
