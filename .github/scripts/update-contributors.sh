#!/usr/bin/env bash
set -euo pipefail

REPO="${1:-CrossPaste/crosspaste-desktop}"
cd "$(dirname "$0")/../.."

logins=()
while read -r login; do
  logins+=("$login")
done < <(gh api "repos/${REPO}/contributors?per_page=100" --paginate --jq '.[] | select(.type == "User") | .login')

if [ ${#logins[@]} -eq 0 ]; then
  echo "No contributors found, aborting to avoid emptying the README section." >&2
  exit 1
fi

if [ -n "${ENSURE_LOGIN:-}" ] && ! printf '%s\n' "${logins[@]}" | grep -qxF "$ENSURE_LOGIN"; then
  logins+=("$ENSURE_LOGIN")
fi

html=""
for login in "${logins[@]}"; do
  html+="<a href=\"https://github.com/${login}\"><img src=\"https://wsrv.nl/?url=github.com/${login}.png&w=120&h=120&fit=cover&mask=circle&output=png\" width=\"60\" height=\"60\" alt=\"${login}\" /></a>&nbsp;"
done

for file in README.md README.zh-CN.md; do
  CONTRIBUTORS_HTML="$html" perl -0777 -pi -e \
    's/<!-- contributors -->.*?<!-- contributors -->/"<!-- contributors -->" . $ENV{CONTRIBUTORS_HTML} . "<!-- contributors -->"/se' \
    "$file"
done
