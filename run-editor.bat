@echo off
title RSPSi Map Editor
cd /d "%~dp0"
call gradlew.bat Editor:run
pause
