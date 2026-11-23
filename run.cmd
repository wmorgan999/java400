@echo off
rem ---------------------------------------------------------------
rem  Reads rows from a DB2 for i table (default NEWSYSGEN.AITR080901)
rem
rem  Usage:  run.cmd [startRow]
rem     startRow  optional 1-based row to start at (default 1)
rem
rem  Settings come from environment variables (DB_HOST, DB_LIBRARY,
rem  DB_TABLE, DB_ROWS, DB_USER, DB_PASSWORD), then db.properties.
rem  User/password are prompted for if not found in either.
rem ---------------------------------------------------------------
setlocal
cd /d "%~dp0"

set START_ROW=%~1
if "%START_ROW%"=="" set START_ROW=1

rem Recompile when the class is missing or older than the source
set NEED_COMPILE=0
if not exist classes\ReadAitr080901.class set NEED_COMPILE=1
if "%NEED_COMPILE%"=="0" (
    for /f %%r in ('powershell -NoProfile -Command "if ((Get-Item src\ReadAitr080901.java).LastWriteTime -gt (Get-Item classes\ReadAitr080901.class).LastWriteTime) {1} else {0}"') do set NEED_COMPILE=%%r
)
if "%NEED_COMPILE%"=="1" (
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
