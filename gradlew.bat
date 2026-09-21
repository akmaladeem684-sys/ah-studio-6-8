@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=9.3.1"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin"
set "DIST_ZIP=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
set "DIST_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%"
if not exist "%DIST_HOME%\bin\gradle.bat" (
  if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
  if not exist "%DIST_ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST_ZIP%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%DIST_ZIP%' '%DIST_DIR%\extract'"
  if exist "%DIST_HOME%" rmdir /s /q "%DIST_HOME%"
  move "%DIST_DIR%\extract\gradle-%GRADLE_VERSION%" "%DIST_HOME%" >nul
  rmdir /s /q "%DIST_DIR%\extract"
)
call "%DIST_HOME%\bin\gradle.bat" %*
