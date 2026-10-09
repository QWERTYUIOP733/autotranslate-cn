@echo off
rem ===== Auto Translate CN 一键构建脚本 =====
rem 使用 D 盘已有的 JDK 25 与 Gradle 缓存，避免写入 C 盘
setlocal
set JAVA_HOME=D:\dev\jdk-25
set GRADLE_USER_HOME=D:\dev\gradle-home
cd /d "%~dp0"
call gradlew.bat build --no-daemon
echo.
echo 构建结束。产物位于 build\libs\autotranslate-1.0.0.jar
pause
