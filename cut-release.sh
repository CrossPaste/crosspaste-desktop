#!/usr/bin/env bash
#
# cut-release.sh — cut a release branch from main and bump main to next minor.
#
# Usage:
#   ./cut-release.sh <version>
#
# Example:
#   ./cut-release.sh 2.1.0
#
# What it does:
#   1. Verifies you are on main, tree is clean, main is up to date with origin.
#   2. Verifies crosspaste-version.properties matches <version>.
#   3. Creates release/<major.minor> branch from current main HEAD, pushes it.
#   4. Switches back to main and creates a branch `chore/bump-to-<next-minor>`
#      that bumps crosspaste-version.properties to the next minor (e.g. 2.2.0).
#      Pushes that branch and prints the PR URL hint.
#
# After running this script you must:
#   - Open the PR for the bump branch and merge it into main.
#   - Switch to release/<major.minor> and tag the actual release commit,
#     e.g. `git tag 2.1.0.<rev> && git push origin 2.1.0.<rev>`.
#     The CI workflow `build-release.yml` will pick it up.

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <version>   (e.g. 2.1.0)" >&2
  exit 2
fi

VERSION="$1"

if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Error: version must be MAJOR.MINOR.PATCH (got: $VERSION)" >&2
  exit 2
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

if [[ "$PATCH" != "0" ]]; then
  echo "Error: cut-release.sh is for cutting minor releases (X.Y.0)." >&2
  echo "       For a patch release, work directly on release/$MAJOR.$MINOR." >&2
  exit 2
fi

RELEASE_BRANCH="release/${MAJOR}.${MINOR}"
NEXT_MINOR="${MAJOR}.$((MINOR + 1)).0"
BUMP_BRANCH="chore/bump-to-${NEXT_MINOR}"
PROPS_FILE="app/src/desktopMain/resources/crosspaste-version.properties"

# --- preflight ---

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "$REPO_ROOT" ]]; then
  echo "Error: not inside a git repository." >&2
  exit 1
fi
cd "$REPO_ROOT"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  echo "Error: must be on 'main' (currently on '$CURRENT_BRANCH')." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: working tree is not clean. Commit or stash first." >&2
  exit 1
fi

echo "Fetching origin..."
git fetch origin --tags --prune

if ! git merge-base --is-ancestor origin/main HEAD; then
  echo "Error: local main is behind origin/main. Pull first." >&2
  exit 1
fi
if [[ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
  echo "Error: local main has unpushed commits. Push them first." >&2
  exit 1
fi

if git show-ref --verify --quiet "refs/heads/$RELEASE_BRANCH" \
   || git ls-remote --exit-code --heads origin "$RELEASE_BRANCH" >/dev/null 2>&1; then
  echo "Error: branch $RELEASE_BRANCH already exists." >&2
  exit 1
fi

CURRENT_PROPS_VERSION="$(grep -E '^version=' "$PROPS_FILE" | head -n1 | cut -d= -f2 | tr -d '[:space:]')"
if [[ "$CURRENT_PROPS_VERSION" != "$VERSION" ]]; then
  echo "Error: $PROPS_FILE has version=$CURRENT_PROPS_VERSION but you asked to cut $VERSION." >&2
  echo "       Update the properties file on main first, then re-run." >&2
  exit 1
fi

# --- cut release branch ---

echo
echo "==> Creating $RELEASE_BRANCH at $(git rev-parse --short HEAD)"
git branch "$RELEASE_BRANCH"
git push origin "$RELEASE_BRANCH"

# --- bump main ---

echo
echo "==> Bumping main to $NEXT_MINOR on $BUMP_BRANCH"
git checkout -b "$BUMP_BRANCH"

# In-place edit, BSD/GNU sed compatible.
TMP="$(mktemp)"
awk -v new="$NEXT_MINOR" '
  BEGIN { done = 0 }
  /^version=/ && !done { print "version=" new; done = 1; next }
  { print }
' "$PROPS_FILE" > "$TMP"
mv "$TMP" "$PROPS_FILE"

git add "$PROPS_FILE"
git commit -m ":construction_worker: bump main to $NEXT_MINOR after cutting $RELEASE_BRANCH"
git push -u origin "$BUMP_BRANCH"

# --- next steps ---

echo
echo "==> Done."
echo
echo "Next steps:"
echo "  1. Open a PR for $BUMP_BRANCH -> main and merge it."
echo "       gh pr create --base main --head $BUMP_BRANCH \\"
echo "         --title 'chore: bump main to $NEXT_MINOR' \\"
echo "         --body  'Cut $RELEASE_BRANCH; main moves on to $NEXT_MINOR.'"
echo
echo "  2. Switch to $RELEASE_BRANCH and tag the release commit:"
echo "       git checkout $RELEASE_BRANCH"
echo "       REV=\$(git rev-list --count HEAD)"
echo "       git tag $VERSION.\$REV"
echo "       git push origin $VERSION.\$REV"
echo
echo "  3. After the build succeeds, append a row to doc/MOBILE_COMPAT.md"
echo "     describing any commonMain API changes since the previous tag."
