#!/bin/sh
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the result, UTF-8. A non-zero exit reports stderr to the user.
set -eu
jq -r '(.rawContent // .content) | ascii_upcase'
