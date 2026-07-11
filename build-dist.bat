@echo off
rem Rebuilds the RSPSi distribution zip: gradle distZip + plugins/active +
rem plugins/inactive + root-level launcher, repacked as one bundle zip.
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo Building Editor distribution (gradlew Editor:distZip)...
call ".\gradlew.bat" Editor:distZip
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

set DIST=Editor\build\distributions
set STAGE=%DIST%\stage

if exist "%STAGE%" rd /s /q "%STAGE%"
mkdir "%STAGE%"

echo Extracting base distribution...
powershell -NoProfile -Command "Expand-Archive -Path '%DIST%\RSPSi.zip' -DestinationPath '%STAGE%' -Force"
if errorlevel 1 goto :fail

echo Copying plugins\active and plugins\inactive...
xcopy /E /I /Y "Editor\plugins\active" "%STAGE%\RSPSi\plugins\active" >nul
xcopy /E /I /Y "Editor\plugins\inactive" "%STAGE%\RSPSi\plugins\inactive" >nul

echo Adding root launcher...
copy /Y "Editor\packaging\Run RSPSi.bat" "%STAGE%\RSPSi\Run RSPSi.bat" >nul

set VERSION=
for /f "tokens=2 delims='" %%v in ('findstr /b "version = " build.gradle') do set VERSION=%%v
if "%VERSION%"=="" set VERSION=dev

set OUT=%DIST%\RSPSi-%VERSION%-bundle.zip
if exist "%OUT%" del /f /q "%OUT%"

echo Creating bundle zip...
powershell -NoProfile -Command "Compress-Archive -Path '%STAGE%\RSPSi' -DestinationPath '%OUT%' -Force"
if errorlevel 1 goto :fail

rd /s /q "%STAGE%"

echo.
echo Done: %OUT%
exit /b 0

:fail
echo Distribution build failed.
exit /b 1
