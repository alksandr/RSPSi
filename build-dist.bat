@echo off
rem Rebuilds the RSPSi distribution zip from the obfuscated shadow install (a single fat
rem jar, ProGuard-obfuscated via proguardInstall) + plugins/active + plugins/inactive +
rem root-level launcher, repacked as one bundle zip.
rem
rem Lightweight distribution: no bundled JRE, so the target machine needs a Java 21 runtime
rem on PATH / JAVA_HOME. The fat jar bundles JavaFX (classes + native libs) and launches via
rem com.rspsi.ui.Main (non-Application entry point), so bare `java -jar` works.
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo Building obfuscated shadow distribution (gradlew Editor:proguardInstall)...
call ".\gradlew.bat" Editor:proguardInstall
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

set DIST=Editor\build\distributions
set SRC=Editor\build\install\Editor-shadow
set STAGE=%DIST%\stage

if exist "%STAGE%" rd /s /q "%STAGE%"
mkdir "%STAGE%\RSPSi"

echo Staging distribution...
xcopy /E /I /Y "%SRC%" "%STAGE%\RSPSi" >nul
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
