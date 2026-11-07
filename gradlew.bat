@echo off
setlocal
set "GRADLE_VERSION=8.9"
set "BOOTSTRAP_ROOT=%USERPROFILE%\.gradle\focus-clock-bootstrap"
set "GRADLE_DIR=%BOOTSTRAP_ROOT%\gradle-%GRADLE_VERSION%"
if exist "%GRADLE_DIR%\bin\gradle.bat" goto run_gradle
if not exist "%BOOTSTRAP_ROOT%" mkdir "%BOOTSTRAP_ROOT%"
set "GRADLE_ZIP=%TEMP%\focus-clock-gradle-%GRADLE_VERSION%.zip"
echo Downloading Gradle %GRADLE_VERSION% (requires internet on this computer)...
powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%GRADLE_ZIP%'; Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%BOOTSTRAP_ROOT%' -Force"
if errorlevel 1 (
  echo Gradle bootstrap failed. Install Gradle 8.9 or check the internet connection.
  exit /b 1
)
:run_gradle
call "%GRADLE_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
