@echo off
rem ---------------------------------------------------------------
rem  Reads 100 rows from NEWSYSGEN.AITR080901 on 172.16.0.21
rem
rem  Usage:  run.cmd [startRow]
rem     startRow  optional 1-based row to start at (default 1)
rem
rem  Credentials: set AS400_USER / AS400_PASSWORD beforehand,
rem  or you will be prompted.
rem ---------------------------------------------------------------
setlocal
cd /d "%~dp0"

set START_ROW=%~1
if "%START_ROW%"=="" set START_ROW=1

if "%AS400_USER%"=="" set /p AS400_USER=IBM i user:
if "%AS400_PASSWORD%"=="" (
    for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$p = Read-Host 'IBM i password' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"`) do set "AS400_PASSWORD=%%p"
)

rem Compile if needed
if not exist classes\ReadAitr080901.class (
    if not exist classes mkdir classes
    javac -cp lib\jt400.jar -d classes src\ReadAitr080901.java
    if errorlevel 1 (
        echo Compile failed.
        exit /b 1
    )
)

java -cp "lib\jt400.jar;classes" ReadAitr080901 %START_ROW%
set RC=%ERRORLEVEL%
endlocal & exit /b %RC%
