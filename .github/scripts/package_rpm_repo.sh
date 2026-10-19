#!/usr/bin/env bash
set -euo pipefail

# Build the dnf repository that installed RPMs subscribe to via
# crosspaste.repo: copy the signed RPMs from an input directory into the
# repository directory, generate repodata/ with createrepo_c, detach-sign
# repomd.xml and publish the public key next to it. Only the packages passed
# in (the current release) are listed, so the repository always serves the
# latest version only.
ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
RPM_DIR=$(realpath "${1:?usage: package_rpm_repo.sh <rpm_dir> <repo_dir>}")
REPO_DIR=${2:?usage: package_rpm_repo.sh <rpm_dir> <repo_dir>}
# shellcheck source=.github/scripts/rpm_signing_key.sh
source "$ROOT_DIR/.github/scripts/rpm_signing_key.sh"
trap 'rm -rf "$GNUPGHOME"' EXIT

shopt -s nullglob
RPM_FILES=("$RPM_DIR"/crosspaste-*.rpm)
if [ "${#RPM_FILES[@]}" -eq 0 ]; then
    echo "Error: No crosspaste-*.rpm in $RPM_DIR" >&2
    exit 1
fi

rm -rf "$REPO_DIR"
mkdir -p "$REPO_DIR"
gpg --batch --armor --export "$GPG_FINGERPRINT" > "$REPO_DIR/RPM-GPG-KEY-CrossPaste"
# Refuse unsigned packages or packages signed by another key: the shipped
# crosspaste.repo enables gpgcheck against exactly this key.
rpm --import "$REPO_DIR/RPM-GPG-KEY-CrossPaste"
for RPM_FILE in "${RPM_FILES[@]}"; do
    rpm -K "$RPM_FILE" | grep -F 'digests signatures OK'
    cp "$RPM_FILE" "$REPO_DIR/"
done

createrepo_c --no-database "$REPO_DIR"
gpg --batch --armor --detach-sign --local-user "$GPG_FINGERPRINT" \
    --output "$REPO_DIR/repodata/repomd.xml.asc" "$REPO_DIR/repodata/repomd.xml"
gpg --batch --verify "$REPO_DIR/repodata/repomd.xml.asc" "$REPO_DIR/repodata/repomd.xml"
echo "Repository built in $REPO_DIR:"
ls -la "$REPO_DIR" "$REPO_DIR/repodata"
