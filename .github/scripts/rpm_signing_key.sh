# shellcheck shell=bash
# Sourced by the RPM packaging scripts. Imports the signing key from the
# RPM_SIGNING_KEY environment variable (an ASCII-armored secret key without
# a passphrase; see build-release.yml) into a throwaway GNUPGHOME and sets
# GPG_FINGERPRINT. The caller owns the cleanup of GNUPGHOME.
if [ -z "${RPM_SIGNING_KEY:-}" ]; then
    echo "Error: RPM_SIGNING_KEY is not set (ASCII-armored GPG secret key)" >&2
    exit 1
fi
GNUPGHOME=$(mktemp -d)
export GNUPGHOME
chmod 0700 "$GNUPGHOME"
gpg --batch --quiet --import <<< "$RPM_SIGNING_KEY"
GPG_FINGERPRINT=$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" { print $10; exit }')
test -n "$GPG_FINGERPRINT"
