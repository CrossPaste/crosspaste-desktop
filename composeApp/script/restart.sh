#!/bin/bash

# Check the number of arguments
if [ $# -ne 2 ]; then
    echo "Usage: $0 <pid> <app_path>"
    exit 1
fi

pid=$1
app_path=$2

# Check if the process exists
while kill -0 $pid 2>/dev/null; do
    echo "Process $pid is still running."
    sleep 0.5  # Check every 500 ms
done

echo "Process $pid has stopped."

if open "$app_path"; then
    echo "Application started successfully."
else
    echo "Failed to start the application."
fi

exit 0