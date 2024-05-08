@echo off
:: Check the number of arguments
if "%~2"=="" (
    echo Usage: %0 [pid] [app_path]
    exit /b 1
)

set "PID=%1"
set "APP_PATH=%2"

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
