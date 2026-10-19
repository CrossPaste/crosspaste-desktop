# Publishing CrossPaste to the Microsoft Store

The release build (`build-release.yml`, triggered by the version tag) produces
two MSIX packages with the Store identity (package family
`ShenzhenCompileFutureTech.CrossPaste_gphsk9mrjnczc`) in the versioned OSS
prefix `https://oss.crosspaste.com/<version>/`:

| File | Manifest `Version` | Purpose |
|------|--------------------|---------|
| `crosspaste-<x.y.z>-<revision>.x64.msix` | `x.y.z.<revision>` | Update site / GitHub Release (installed via `crosspaste.appinstaller`) |
| `crosspaste-<x.y.z>-store.x64.msix` | `x.y.z.0` | Microsoft Store submission |

Partner Center only accepts a package whose manifest `Version` ends in `.0`
(Conveyor: "the app.revision number must be zero as the store uses the
revision number for its own purposes"), so the site package is rejected on
upload. The Store package is the same build with `app.revision` forced to `0`
(`conveyor -Kapp.revision=0 -f build.conveyor.conf make windows-msix`); the
workflow verifies its manifest (`Version`, `app\bin\crosspaste-cli.exe`, the
`crosspaste` / `crosspaste-cli` execution aliases) before listing it in
`checksum.txt`. Always submit the Store package built by CI from the release
tag — a locally built one may not match the code published on GitHub, and the
Store shows `x.y.z.0` for both, so the mismatch would be invisible.

This document describes how Store publishing is automated and what has to be
done by hand exactly once.

## What is automated

`scripts/publish-msstore.sh` (invoked by the manual
[`msstore-publish.yml`](../../.github/workflows/msstore-publish.yml) workflow,
or runnable locally on macOS/Linux) performs, per release:

1. Download `crosspaste-<x.y.z>-store.x64.msix` for the given version from
   `https://oss.crosspaste.com/<version>/`, verify its SHA256 against
   `checksum.txt`, and check that the manifest `Version` is `x.y.z.0`.
2. Upload the package into a fresh pending submission (`msstore publish
   --noCommit`). This runs first because, for a live app, msstore-cli deletes
   any existing pending submission and creates a new one — metadata written
   earlier would be lost.
3. Fetch that pending submission, patch the "What's new in this version"
   release notes for the given version's section — `whats-new/en.md` → the
   `en-us` listing, `whats-new/zh.md` → the `zh-cn` listing (each capped at
   the Store's 1500-character limit) — and push it back with
   `msstore submission updateMetadata`.
4. Commit the submission and poll until certification processing starts.

Everything else in the listing (description, screenshots, markets, pricing)
stays untouched: the script fetches the live submission and only rewrites
`ReleaseNotes` on listings that already exist in it.

## One-time setup

The Store submission API cannot create the first listing. Do this once:

1. **First submission by hand.** In [Partner Center](https://partner.microsoft.com/),
   complete the app's first submission: listing texts, screenshots, age
   rating, markets, pricing, and upload the MSIX manually. Wait until the app
   is published and live.
2. **Service principal.** The workflow reuses the Entra ID application the
   Conveyor release build already authenticates with (repo secrets
   `WINDOWS_TENANT_ID`, `WINDOWS_CLIENT_ID`, `WINDOWS_CLIENT_SECRET`, consumed
   by `build.conveyor.conf`). If setting up from scratch: associate a
   Microsoft Entra tenant with the Partner Center account, register an
   application in Entra ID, and add it under Partner Center → Account
   settings → User management → Microsoft Entra applications with the
   **Manager** role.
3. **GitHub secrets.** Besides the existing `WINDOWS_TENANT_ID`,
   `WINDOWS_CLIENT_ID`, `WINDOWS_CLIENT_SECRET`, and `WINDOWS_STORE_ID` (the
   `9N…` Store product ID, Partner Center → the app → Product identity), add
   one new secret `WINDOWS_SELLER_ID` — the Partner Center Seller ID
   (Partner Center → Account settings → Organization profile → Legal info),
   required by `msstore reconfigure`.
4. **Protected environment.** Create a GitHub Environment named
   `msstore-publish` (Settings → Environments) and restrict its deployment
   branches to `main` only. The workflow job runs in this environment, so
   GitHub enforces server-side that a manual dispatch cannot execute a
   modified script from an arbitrary branch with the Store credentials.
   Prefer adding `WINDOWS_SELLER_ID` as an environment secret rather than a
   repo secret (the other `WINDOWS_*` secrets must stay repo-level because
   `build-release.yml` consumes them).

## Per-release usage

After the GitHub Release for `<version>` is out (the OSS bucket is populated
by `build-release.yml` at tag time):

- **CI:** Actions → "Publish to Microsoft Store" → Run workflow with
  `version = <full version>` (e.g. `2.1.7.2452`), mode `publish`.
- **Locally:** `msstore reconfigure ...` once on the machine, then
  `WINDOWS_STORE_ID=9N... scripts/publish-msstore.sh 2.1.7.2452`.
  Use `--dry-run` to preview the extracted release notes without touching the
  pending submission.
- **Manual upload (fallback):** download
  `https://oss.crosspaste.com/<version>/crosspaste-<x.y.z>-store.x64.msix`,
  verify it against `checksum.txt`, and drag it into the submission's
  Packages page in Partner Center. Only if CI did not produce the package,
  build it locally from the release tag (`git checkout <tag>`) with
  `conveyor --cache-limit=40 -Kapp.revision=0 -f build.conveyor.conf make windows-msix`
  (or a gitignored local config that includes `cli.conveyor.conf` and the
  Store credentials) and confirm the manifest `Version` is `x.y.z.0`:
  `unzip -p <file>.msix AppxManifest.xml | tr '\n' ' ' | grep -o '<Identity [^>]*>'`.

The release notes are taken from the `# [x.y.z]` section matching the given
version (not simply the newest section), so re-publishing an older version
cannot ship the wrong notes; the script fails if `whats-new/en.md` has no
section for that version.

Certification typically takes from a few hours up to three business days;
progress is visible in Partner Center or via
`msstore submission status <productId>`.
