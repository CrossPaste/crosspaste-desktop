#!/usr/bin/env bash
#
# Publish a CrossPaste release to the Microsoft Store via msstore-cli.
#
# What it does, in order:
#   1. Downloads the Store-identity MSIX for <version> from the OSS release
#      bucket (or uses --msix <path>) and verifies its SHA256 against
#      checksum.txt.
#   2. Uploads the MSIX into a new pending submission (`msstore publish
#      --noCommit`). This must happen FIRST: for a live app msstore-cli
#      deletes any existing pending submission and creates a fresh one, so
#      metadata written earlier would be lost.
#   3. Fetches that fresh pending submission (`msstore submission get`),
#      patches the per-listing release notes ("What's new") for <version>'s
#      section of whats-new/en.md (en-us) and whats-new/zh.md (zh-cn), and
#      pushes it back with `msstore submission updateMetadata`.
#   4. Commits the submission (`msstore submission publish`) and polls until
#      the Store accepts or rejects it.
#
# Prerequisites:
#   - msstore-cli on PATH (brew install microsoft/msstore-cli/msstore-cli) and
#     already configured: msstore reconfigure --tenantId ... --sellerId ...
#     --clientId ... --clientSecret ...
#   - The app must already be live in the Store; the first submission (listing,
#     screenshots, age rating, markets) is manual in Partner Center. See
#     doc/en/MicrosoftStore.md.
#   - jq, curl, shasum or sha256sum.
#
# Usage:
#   scripts/publish-msstore.sh <full-version> [--msix <path>] [--product-id <id>] [--dry-run]
#     <full-version>   e.g. 2.1.7.2452 (the git tag / OSS prefix)
#     --msix <path>    use a local MSIX instead of downloading from OSS
#     --product-id     Store product ID; defaults to $WINDOWS_STORE_ID (same
#                      secret the Conveyor release build uses)
#     --dry-run        stop before any msstore call that mutates the submission

set -euo pipefail

OSS_BASE="https://oss.crosspaste.com"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WHATS_NEW_DIR="$REPO_ROOT/app/src/desktopMain/resources/whats-new"
# Store release notes are capped at 1500 characters per listing. awk length()
# counts characters on gawk (CI) but bytes on macOS awk, so a local --dry-run
# truncates CJK notes more aggressively than the real CI run; both stay under
# the Store limit.
RELEASE_NOTES_MAX=1490

VERSION="${1:-}"
[[ -n "$VERSION" && "$VERSION" != --* ]] || {
  echo "Usage: $0 <full-version> [--msix <path>] [--product-id <id>] [--dry-run]" >&2
  exit 2
}
shift

# Strict N.N.N.N — also the injection barrier for the version coming in from
# the workflow_dispatch input via the environment.
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "Invalid version '$VERSION': expected N.N.N.N (e.g. 2.1.7.2452)." >&2
  exit 2
}

MSIX=""
PRODUCT_ID="${WINDOWS_STORE_ID:-}"
DRY_RUN=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --msix) [[ $# -ge 2 ]] || { echo "Missing value for --msix." >&2; exit 2; }; MSIX="$2"; shift 2 ;;
    --product-id) [[ $# -ge 2 ]] || { echo "Missing value for --product-id." >&2; exit 2; }; PRODUCT_ID="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$PRODUCT_ID" ]] || { echo "Missing Store product ID: set WINDOWS_STORE_ID or pass --product-id." >&2; exit 2; }
[[ "$DRY_RUN" == 1 ]] || command -v msstore >/dev/null || { echo "msstore-cli not found on PATH." >&2; exit 2; }
command -v jq >/dev/null || { echo "jq not found on PATH." >&2; exit 2; }

if command -v sha256sum >/dev/null; then
  SHA256="sha256sum"
else
  SHA256="shasum -a 256"
fi

# 2.1.7.2452 -> version=2.1.7 revision=2452 -> crosspaste-2.1.7-2452.x64.msix
BASE_VERSION="${VERSION%.*}"
REVISION="${VERSION##*.}"
MSIX_NAME="crosspaste-${BASE_VERSION}-${REVISION}.x64.msix"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

if [[ -z "$MSIX" ]]; then
  echo "Downloading $MSIX_NAME from $OSS_BASE/$VERSION/ ..."
  curl -fsS -o "$WORK_DIR/$MSIX_NAME" "$OSS_BASE/$VERSION/$MSIX_NAME"
  curl -fsS -o "$WORK_DIR/checksum.txt" "$OSS_BASE/$VERSION/checksum.txt"
  EXPECTED="$(awk -v f="$MSIX_NAME" '{sub(/^\*/, "", $2)} $2 == f {print $1}' "$WORK_DIR/checksum.txt")"
  [[ -n "$EXPECTED" ]] || { echo "$MSIX_NAME not found in checksum.txt." >&2; exit 1; }
  ACTUAL="$($SHA256 "$WORK_DIR/$MSIX_NAME" | awk '{print $1}')"
  [[ "$ACTUAL" == "$EXPECTED" ]] || { echo "SHA256 mismatch for $MSIX_NAME: expected $EXPECTED, got $ACTUAL." >&2; exit 1; }
  echo "SHA256 verified: $ACTUAL"
  MSIX="$WORK_DIR/$MSIX_NAME"
else
  [[ -f "$MSIX" ]] || { echo "MSIX not found: $MSIX" >&2; exit 1; }
fi

# Extract the "# [$BASE_VERSION] - date" section (not blindly the newest one,
# so re-publishing an older version cannot ship the wrong notes) from a
# whats-new file as plain text: section titles ("## 🛡️ Title") become bullet
# lines, paragraphs follow verbatim. Sections that would exceed the Store's
# release-notes limit are dropped whole, so the output never cuts mid-sentence
# or mid-character.
release_notes() {
  local file="$1"
  awk -v max="$RELEASE_NOTES_MAX" -v ver="$BASE_VERSION" '
    function flush() {
      if (buf == "" || done) { buf = ""; return }
      candidate = out (out == "" ? "" : "\n\n") buf
      if (length(candidate) <= max) out = candidate; else done = 1
      buf = ""
    }
    /^# \[/ { if (started) exit; if (index($0, "# [" ver "]") == 1) started = 1; next }
    !started { next }
    /^## /  { flush(); sub(/^## /, ""); buf = "• " $0; next }
    NF      { buf = buf (buf == "" ? "" : "\n") $0 }
    END     { flush(); print out }
  ' "$file"
}

NOTES_EN="$(release_notes "$WHATS_NEW_DIR/en.md")"
NOTES_ZH="$(release_notes "$WHATS_NEW_DIR/zh.md")"
[[ -n "$NOTES_EN" ]] || { echo "No '# [$BASE_VERSION]' section found in whats-new/en.md." >&2; exit 1; }
[[ -n "$NOTES_ZH" ]] || echo "WARNING: no '# [$BASE_VERSION]' section in whats-new/zh.md — zh-cn release notes will not be updated." >&2
echo "--- en-us release notes (${#NOTES_EN} chars) ---"
echo "$NOTES_EN"
echo "--- zh-cn release notes ---"
echo "$NOTES_ZH"
echo "-----------------------------------------------"

if [[ "$DRY_RUN" == 1 ]]; then
  echo "Dry run: would upload $MSIX to product $PRODUCT_ID, then patch the pending submission's release notes, publish, poll."
  exit 0
fi

# Package first: for a live app `msstore publish` deletes any pending
# submission and creates a new one, so metadata must be written afterwards.
echo "Uploading package into a fresh pending submission ..."
msstore publish --inputFile "$MSIX" --appId "$PRODUCT_ID" --noCommit

echo "Fetching the pending submission ..."
# stdout carries human status lines before the pretty-printed JSON object;
# keep everything from the first line starting with '{'.
# $'...' yields a literal ESC byte so the ANSI strip also works with BSD sed.
SUBMISSION_JSON="$(msstore submission get "$PRODUCT_ID" | tr -d '\r' | sed -e $'s/\x1b\\[[0-9;]*m//g' | awk '/^\{/{found=1} found')"
jq -e . >/dev/null <<<"$SUBMISSION_JSON" || { echo "Could not parse submission JSON from 'msstore submission get'." >&2; exit 1; }

# `submission get` falls back to the LAST PUBLISHED submission when no pending
# one exists — updating that would be wrong, so insist on a draft.
jq -e '.Status and .Status != "Published"' >/dev/null <<<"$SUBMISSION_JSON" || {
  echo "Fetched submission is not a pending draft (Status=$(jq -r .Status <<<"$SUBMISSION_JSON")); aborting before touching metadata." >&2
  exit 1
}

# msstore-cli serializes with an upper-case-first naming policy: the paths are
# .Listings["en-us"].BaseListing.ReleaseNotes (NOT camelCase).
jq -e '.Listings["en-us"].BaseListing' >/dev/null <<<"$SUBMISSION_JSON" || {
  echo "Submission has no en-us listing (or the JSON shape changed); refusing to guess." >&2
  exit 1
}
# -c keeps the argv line short: the full submission is passed to msstore-cli
# as a single argument and Windows caps a command line at ~32k characters.
PATCHED_JSON="$(jq -c --arg en "$NOTES_EN" --arg zh "$NOTES_ZH" '
  .Listings["en-us"].BaseListing.ReleaseNotes = $en
  | if .Listings["zh-cn"] and ($zh != "") then .Listings["zh-cn"].BaseListing.ReleaseNotes = $zh else . end
' <<<"$SUBMISSION_JSON")"
# Belt and braces: fail loudly if the patch did not land (this is exactly the
# failure mode a silent key-casing mismatch would produce).
jq -e --arg en "$NOTES_EN" '.Listings["en-us"].BaseListing.ReleaseNotes == $en' >/dev/null <<<"$PATCHED_JSON" || {
  echo "Release-notes patch did not take effect; aborting." >&2
  exit 1
}
if [[ -n "$NOTES_ZH" ]] && ! jq -e '.Listings["zh-cn"]' >/dev/null <<<"$SUBMISSION_JSON"; then
  echo "WARNING: no zh-cn listing in the submission — Chinese release notes will NOT be published." >&2
  echo "         Add Chinese (Simplified) as a listing language in Partner Center first." >&2
fi

echo "Updating submission metadata (release notes) ..."
msstore submission updateMetadata "$PRODUCT_ID" "$PATCHED_JSON"

echo "Committing the submission ..."
msstore submission publish "$PRODUCT_ID"

echo "Polling submission status until the Store finishes processing ..."
msstore submission poll "$PRODUCT_ID"
