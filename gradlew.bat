@echo off
setlocal
set APP_HOME=%~dp0
set GRADLE_VERSION=8.7
set DIST_NAME=gradle-%GRADLE_VERSION%-bin.zip
set DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set CACHE_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%
set INSTALL_DIR=%CACHE_DIR%\gradle-%GRADLE_VERSION%
if exist "%INSTALL_DIR%\bin\gradle.bat" goto run
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
echo Baixando Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%CACHE_DIR%\%DIST_NAME%'"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%CACHE_DIR%\%DIST_NAME%' '%CACHE_DIR%'"
:run
call "%INSTALL_DIR%\bin\gradle.bat" %*
endlocal
