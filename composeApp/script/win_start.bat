@echo off
:: Determine the application path relative to this script
set "APP_PATH=%~dp0clipevery.exe"

:: Check if a PID is provided
if "%~1" == "" (
    goto START_APP
)

set "PID=%1"

:: Validate if PID is a number
echo %PID% | findstr /R /X "[0-9][0-9]*" >nul
if %ERRORLEVEL% neq 0 (
    echo Warning: Provided PID is not a number. Starting the application immediately.
    goto START_APP
)

:CHECK_LOOP
:: Check if the process exists
tasklist /FI "PID eq %PID%" /NH | findstr /I "%PID%"
if %ERRORLEVEL% neq 0 (
    echo Process %PID% does not exist.
    goto START_APP
)

:: Process exists, wait for 500 ms
timeout /T 0.5 /NOBREAK > NUL
goto CHECK_LOOP

:START_APP
echo Process %PID% has been stopped.

:: Start the application
start "" "%APP_PATH%"
if %ERRORLEVEL% neq 0 (
    echo Failed to start the application.
    exit /b 1
)
echo Application started successfully.
exit /b 0
