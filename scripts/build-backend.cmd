@echo off
setlocal

set "REPO_DIR=%~dp0.."
set "MVN=mvn.cmd"

where mvn.cmd >nul 2>nul
if errorlevel 1 (
  if exist "%REPO_DIR%\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MVN=%REPO_DIR%\apache-maven-3.9.6\bin\mvn.cmd"
  ) else (
    echo Maven was not found. Install Maven or place apache-maven-3.9.6 outside Git tracking.
    exit /b 1
  )
)

cd /d "%REPO_DIR%"
call "%MVN%" -DskipTests package
