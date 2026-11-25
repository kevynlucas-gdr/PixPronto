@echo off
setlocal
set VERSION=8.13.2
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set BASE=%GRADLE_USER_HOME%\pixpronto-gradle
set GRADLE_HOME=%BASE%\gradle-%VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$z='%BASE%\gradle-%VERSION%-bin.zip'; if (!(Test-Path $z)) { Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile $z }; Expand-Archive -Force $z '%BASE%'"
)
if not exist "%~dp0gradle\wrapper\gradle-wrapper.jar" call "%GRADLE_HOME%\bin\gradle.bat" -p "%~dp0" wrapper --gradle-version %VERSION% --distribution-type bin
call "%GRADLE_HOME%\bin\gradle.bat" -p "%~dp0" %*
