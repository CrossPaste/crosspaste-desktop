#!/bin/bash

# Define the application path relative to this script
app_path="$(dirname "$0")/crosspaste"

# Check if a PID is provided and if it is a number
if [ $# -eq 1 ]; then
    pid=$1
    if [[ $pid =~ ^[0-9]+$ ]]; then
        # Check if the process exists and wait for it to stop
        while kill -0 $pid 2>/dev/null; do
            echo "Process $pid is still running."
            sleep 0.5  # Check every 500 ms
        done
        echo "Process $pid has stopped."
    else
        echo "Warning: Provided PID is not a number. Starting the application immediately."
    fi
fi

# Set the DISPLAY and attempt to start the application
export DISPLAY=$DISPLAY
if "$app_path"; then
    echo "Application started successfully."
    exit 0
else
    echo "Failed to start the application."
    exit 1
fi


