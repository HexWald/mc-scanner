@echo off
chcp 65001 >nul
title MC Scanner Console
echo ============================================
echo MC Scanner - Debug Console
echo ============================================
echo.
echo Starting application...
echo Console output will appear below:
echo.
java -Dfile.encoding=UTF-8 -jar MCScanner.jar
pause
