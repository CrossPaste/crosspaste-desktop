#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-CrossPaste/crosspaste-desktop}"
cd "$(dirname "$0")/../.."

html=""
while read -r login; do
  html+="<a href=\"https://github.com/${login}\"><img src=\"https://wsrv.nl/?url=github.com/${login}.png&w=120&h=120&fit=cover&mask=circle\" width=\"60\" height=\"60\" alt=\"${login}\" /></a>&nbsp;"
done < <(gh api "repos/${REPO}/contributors?per_page=100" --paginate --jq '.[] | select(.type == "User") | .login')

if [ -z "$html" ]; then
  echo "No contributors found, aborting to avoid emptying the README section." >&2
  exit 1
fi

for file in README.md README.zh-CN.md; do
  CONTRIBUTORS_HTML="$html" perl -0777 -pi -e \
    's/<!-- contributors -->.*?<!-- contributors -->/"<!-- contributors -->" . $ENV{CONTRIBUTORS_HTML} . "<!-- contributors -->"/se' \
    "$file"
done
