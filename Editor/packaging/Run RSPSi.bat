@echo off
rem plugins\active is loaded relative to CWD (ClientPluginLoader), so
rem cwd must be this folder, not bin\ — bin\RSPSi.bat does not cd itself.
cd /d "%~dp0"
call bin\RSPSi.bat
