#!/usr/bin/env bash
#
# Publish a CrossPaste release to the Microsoft Store via msstore-cli.
#
# What it does, in order:
#   1. Downloads the Store-identity MSIX for <version> from the OSS release
#      bucket (or uses --msix <path>) and verifies its SHA256 against
#      checksum.txt.
#   2. If msstore/metadata.json exists, patches the per-listing release notes
#      ("What's new") from whats-new/en.md (en-us) and whats-new/zh.md (zh-cn)
#      and pushes it with `msstore submission updateMetadata`.
#   3. Uploads the MSIX into the pending submission (`msstore publish
#      --noCommit`), then commits it (`msstore submission publish`) and polls
#      until the Store accepts or rejects it.
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
METADATA_BASE="$REPO_ROOT/msstore/metadata.json"
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

MSIX=""
PRODUCT_ID="${WINDOWS_STORE_ID:-}"
DRY_RUN=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --msix) MSIX="$2"; shift 2 ;;
    --product-id) PRODUCT_ID="$2"; shift 2 ;;
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
  EXPECTED="$(awk -v f="$MSIX_NAME" '$2 == f {print $1}' "$WORK_DIR/checksum.txt")"
  [[ -n "$EXPECTED" ]] || { echo "$MSIX_NAME not found in checksum.txt." >&2; exit 1; }
  ACTUAL="$($SHA256 "$WORK_DIR/$MSIX_NAME" | awk '{print $1}')"
  [[ "$ACTUAL" == "$EXPECTED" ]] || { echo "SHA256 mismatch for $MSIX_NAME: expected $EXPECTED, got $ACTUAL." >&2; exit 1; }
  echo "SHA256 verified: $ACTUAL"
  MSIX="$WORK_DIR/$MSIX_NAME"
else
  [[ -f "$MSIX" ]] || { echo "MSIX not found: $MSIX" >&2; exit 1; }
fi

# Extract the latest "# [x.y.z] - date" section from a whats-new file as plain
# text: section titles ("## 🛡️ Title") become bullet lines, paragraphs follow
# verbatim. Sections that would exceed the Store's release-notes limit are
# dropped whole, so the output never cuts mid-sentence or mid-character.
release_notes() {
  local file="$1"
  awk -v max="$RELEASE_NOTES_MAX" '
    function flush() {
      if (buf == "" || done) { buf = ""; return }
      candidate = out (out == "" ? "" : "\n\n") buf
      if (length(candidate) <= max) out = candidate; else done = 1
      buf = ""
    }
    /^# \[/ { if (started) exit; started = 1; next }
    !started { next }
    /^## /  { flush(); sub(/^## /, ""); buf = "• " $0; next }
    NF      { buf = buf (buf == "" ? "" : "\n") $0 }
    END     { flush(); print out }
  ' "$file"
}

NOTES_EN="$(release_notes "$WHATS_NEW_DIR/en.md")"
NOTES_ZH="$(release_notes "$WHATS_NEW_DIR/zh.md")"
[[ -n "$NOTES_EN" ]] || { echo "Could not extract release notes from whats-new/en.md." >&2; exit 1; }
echo "--- en-us release notes (${#NOTES_EN} chars) ---"
echo "$NOTES_EN"
echo "--- zh-cn release notes ---"
echo "$NOTES_ZH"
echo "-----------------------------------------------"

if [[ -f "$METADATA_BASE" ]]; then
  # Only patch releaseNotes on listings that already exist in the baseline;
  # everything else in the submission metadata stays exactly as checked in.
  jq --arg en "$NOTES_EN" --arg zh "$NOTES_ZH" '
    if .listings["en-us"] then .listings["en-us"].baseListing.releaseNotes = $en else . end
    | if .listings["zh-cn"] and ($zh != "") then .listings["zh-cn"].baseListing.releaseNotes = $zh else . end
  ' "$METADATA_BASE" > "$WORK_DIR/metadata.json"
  METADATA_JSON="$(cat "$WORK_DIR/metadata.json")"
  if [[ -n "$NOTES_ZH" ]] && ! jq -e '.listings["zh-cn"]' "$METADATA_BASE" >/dev/null; then
    echo "WARNING: no zh-cn listing in $METADATA_BASE — Chinese release notes will NOT be published." >&2
    echo "         Add Chinese (Simplified) as a listing language in Partner Center, then refresh the baseline." >&2
  fi
else
  echo "WARNING: $METADATA_BASE not found — skipping release-notes metadata update."
  echo "         (Run the get-base-metadata workflow once the app is live and check the JSON in as msstore/metadata.json.)"
  METADATA_JSON=""
fi

if [[ "$DRY_RUN" == 1 ]]; then
  echo "Dry run: would upload $MSIX to product $PRODUCT_ID, update metadata ($([[ -n "$METADATA_JSON" ]] && echo yes || echo no)), publish, poll."
  exit 0
fi

if [[ -n "$METADATA_JSON" ]]; then
  echo "Updating submission metadata (release notes) ..."
  msstore submission updateMetadata "$PRODUCT_ID" "$METADATA_JSON"
fi

echo "Uploading package into the pending submission ..."
msstore publish --inputFile "$MSIX" --appId "$PRODUCT_ID" --noCommit

echo "Committing the submission ..."
msstore submission publish "$PRODUCT_ID"

echo "Polling submission status until the Store finishes processing ..."
msstore submission poll "$PRODUCT_ID"
