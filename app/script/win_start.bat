@echo off
:: Check if at least 1 parameter is provided (exe path)
if "%~1" == "" (
    echo Error: Missing executable path parameter.
    exit /b 1
)

:: Set APP_PATH from the first parameter
set "APP_PATH=%~1"

:: Check if a PID is provided as the second parameter
if "%~2" == "" (
    echo No PID provided. Starting the application immediately.
    goto START_APP
)

set "PID=%~2"

echo Waiting for process with PID %PID% to terminate...

:CHECK_LOOP
:: Check if the process exists
tasklist /FI "PID eq %PID%" 2>nul | find "%PID%" >nul
if %ERRORLEVEL% neq 0 (
    echo Process with PID %PID% no longer exists or was terminated.
    goto START_APP
)

:: Process exists, wait for 1 second (timeout doesn't support decimals in batch)
timeout /T 1 /NOBREAK >nul
goto CHECK_LOOP

:START_APP
echo Starting application: "%APP_PATH%"

:: Start the application with proper handling of paths with spaces
start "" "%APP_PATH%"
if %ERRORLEVEL% neq 0 (
    echo Failed to start the application.
    exit /b 1
)
echo Application started successfully.
exit /b 0