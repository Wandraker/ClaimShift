@echo off
setlocal EnableExtensions
set "APP_HOME=%~dp0"
set "WRAPPER_DIR=%APP_HOME%gradle\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar"
set "WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v9.2.1/gradle/wrapper/gradle-wrapper.jar"
set "WRAPPER_SHA256=423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9"

if not exist "%WRAPPER_JAR%" (
  echo [ClaimShift] Gradle wrapper JAR is missing. Downloading the official Gradle 9.2.1 wrapper...
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop'; $url='%WRAPPER_URL%'; $out='%WRAPPER_JAR%'; $expected='%WRAPPER_SHA256%'; Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $out; $actual=(Get-FileHash -Algorithm SHA256 -LiteralPath $out).Hash.ToLowerInvariant(); if ($actual -ne $expected) { Remove-Item -Force -LiteralPath $out -ErrorAction SilentlyContinue; throw ('Gradle wrapper checksum mismatch. Expected ' + $expected + ', got ' + $actual) }"
  if errorlevel 1 (
    echo [ClaimShift] Could not bootstrap the Gradle wrapper.
    exit /b 1
  )
)

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

if not exist "%JAVA_EXE%" if defined JAVA_HOME (
  echo ERROR: JAVA_HOME points to an invalid JDK: %JAVA_HOME%
  exit /b 1
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
