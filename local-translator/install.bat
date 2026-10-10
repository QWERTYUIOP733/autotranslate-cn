@echo off
chcp 65001 >nul
title 自动汉化模组 - 安装本地AI翻译
echo ================================================
echo   第一步：安装 Python 依赖 (argostranslate)
echo ================================================
cd /d "%~dp0"
if not exist venv\Scripts\python.exe (
    echo [创建虚拟环境 venv ...]
    python -m venv venv
)
venv\Scripts\python.exe -m pip install --upgrade pip
venv\Scripts\python.exe -m pip install argostranslate
if errorlevel 1 (
    echo [依赖安装失败，请检查网络后重试]
    pause
    exit /b 1
)
echo.
echo ================================================
echo   第二步：下载 en-^>zh 语言模型（约 200MB）
echo ================================================
venv\Scripts\python.exe install_model.py
echo.
echo 安装完成！运行 start.bat 启动本地翻译服务。
pause
