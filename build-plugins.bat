@echo off
title RSPSi Plugin Builder
cd /d "%~dp0"
call gradlew.bat Plugins:OSRSPlugin:buildAndMove Plugins:OSRSPlugin-218:buildAndMove Plugins:GallifreyPlugin:buildAndMove
echo.
echo Plugin jars copied to Editor\plugins\inactive
pause
