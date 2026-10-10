@echo off
chcp 65001 >nul
title 自动汉化模组 - 本地AI翻译服务
echo ================================================
echo   自动汉化模组 - 本地 AI 翻译服务 (Argos)
echo   首次使用请先运行 install.bat 安装模型
echo ================================================
cd /d "%~dp0"
venv\Scripts\python.exe service.py --port 5000
pause
