# Publishing CrossPaste to the Microsoft Store

CrossPaste's release build already produces a Store-identity MSIX
(`crosspaste-<version>-<revision>.x64.msix`, package family
`ShenzhenCompileFutureTech.CrossPaste_gphsk9mrjnczc`). This document describes
how Store publishing is automated and what has to be done by hand exactly once.

## What is automated

`scripts/publish-msstore.sh` (invoked by the manual
[`msstore-publish.yml`](../../.github/workflows/msstore-publish.yml) workflow,
or runnable locally on macOS/Linux) performs, per release:

1. Download the MSIX for the given version from
   `https://oss.crosspaste.com/<version>/` and verify its SHA256 against
   `checksum.txt`.
2. Patch the "What's new in this version" release notes into the submission
   metadata — `whats-new/en.md` → the `en-us` listing, `whats-new/zh.md` → the
   `zh-cn` listing (each truncated to the Store's 1500-character limit).
3. Upload the package into the pending submission, commit it, and poll until
   certification processing starts.

Everything else in the listing (description, screenshots, markets, pricing)
stays untouched: the script only rewrites `releaseNotes` on listings that
already exist in the checked-in baseline `msstore/metadata.json`.

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
4. **Metadata baseline.** Run the `Publish to Microsoft Store` workflow once
   with mode `get-base-metadata`, copy the submission JSON from the log into
   `msstore/metadata.json`, and commit it. Release-notes patching is skipped
   with a warning until this file exists.

## Per-release usage

After the GitHub Release for `<version>` is out (the OSS bucket is populated
by `build-release.yml` at tag time):

- **CI:** Actions → "Publish to Microsoft Store" → Run workflow with
  `version = <full version>` (e.g. `2.1.7.2452`), mode `publish`.
- **Locally:** `msstore reconfigure ...` once on the machine, then
  `WINDOWS_STORE_ID=9N... scripts/publish-msstore.sh 2.1.7.2452`.
  Use `--dry-run` to preview the extracted release notes without touching the
  pending submission.

Certification typically takes from a few hours up to three business days;
progress is visible in Partner Center or via
`msstore submission status <productId>`.
