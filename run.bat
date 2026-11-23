@echo off
rem ---------------------------------------------------------------
rem  Reads rows from a DB2 for i table (default NEWSYSGEN.AITR080901)
rem
rem  Usage:  run.cmd [startRow]
rem     startRow  optional 1-based row to start at (default 1)
rem
rem  Settings come from environment variables (DB_HOST, DB_LIBRARY,
rem  DB_TABLE, DB_ROWS, DB_FORMAT, DB_USER, DB_PASSWORD), then
rem  db.properties. User/password are prompted for if not found.
rem
rem  Java: uses the newest installed JDK (8 or later). To force one,
rem  set JAVA400_JDK to its folder, e.g.
rem     set JAVA400_JDK=C:\Program Files\Microsoft\jdk-11.0.32.101-hotspot
rem ---------------------------------------------------------------
setlocal
cd /d "%~dp0"

set START_ROW=%~1
if "%START_ROW%"=="" set START_ROW=1

set JDK_MAJOR=
set JDK_HOME=
for /f "usebackq tokens=1* delims=|" %%a in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0find-jdk.ps1"`) do (
    set "JDK_MAJOR=%%a"
    set "JDK_HOME=%%b"
)
if "%JDK_HOME%"=="" (
    echo No usable JDK found. Install JDK 8 or later, or set JAVA400_JDK.
    exit /b 1
)
set "JAVA_HOME=%JDK_HOME%"
set "PATH=%JDK_HOME%\bin;%PATH%"
echo Using Java %JDK_MAJOR% from %JDK_HOME% 1>&2

rem Target Java 8 class files so the build runs on any JDK 8+.
set RELEASE_OPT=
if %JDK_MAJOR% GEQ 9 set RELEASE_OPT=--release 8

rem Compile into build\ (separate from Eclipse's output folder) every run.
if exist build rmdir /s /q build
mkdir build
"%JDK_HOME%\bin\javac" %RELEASE_OPT% -nowarn -cp lib\jt400.jar -d build src\ReadAitr080901.java
if errorlevel 1 (
    echo Compile failed.
    exit /b 1
)

"%JDK_HOME%\bin\java" -cp "lib\jt400.jar;build" ReadAitr080901 %START_ROW%
set RC=%ERRORLEVEL%
endlocal & exit /b %RC%
