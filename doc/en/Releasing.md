# Releasing

This document describes how CrossPaste Desktop versions are released, and the
discipline required so that:

- `main` can keep moving even while a release line is being maintained.
- Multiple large features can land on `main` behind flags without blocking
  smaller releases.
- The mobile project (which reuses `commonMain`) always has a stable, named
  anchor to sync from.

If you only want the recipes, jump to
[Cutting a minor release (X.Y.0)](#cutting-a-minor-release-xy0)
or [Cutting a patch release (X.Y.Z)](#cutting-a-patch-release-xyz).

---

## Branch model

| Branch          | Purpose                                                    |
| --------------- | ---------------------------------------------------------- |
| `main`          | Trunk. Always releasable. Small features and fixes go here directly. Large in-flight features live on feature branches and are merged behind flags ("dark merge"). |
| `release/X.Y`   | Stable line for a given minor version. Cut from `main` when we decide to release `X.Y.0`. Only patch releases (`X.Y.Z`) are cut from here. |
| `feature/*`     | Long-lived branches for big features. Merged into `main` only when the feature compiles and runs cleanly with its flag off. |

### Why release branches?

Tagging directly on `main` couples release stability to trunk velocity: a hotfix
on top of `vX.Y.0` is impossible without dragging in everything that landed on
`main` afterwards. Release branches give us a clean place to cherry-pick the
minimum set of fixes for a patch release.

### Why dark-merge big features?

Long-lived branches diverge from `main` and become painful to rebase. Merging
early — but keeping the feature dark behind a flag — keeps `main` integrated
without blocking the rest of the project on the feature being "done".

A flag may be a compile-time toggle (e.g. `expect/actual` declared off) or a
runtime `AppEnv` setting. The rule is: with the flag off, the feature must be
invisible and zero-cost — no UI surfaces, no background work, no schema
migrations.

---

## Tag scheme

Tags follow `MAJOR.MINOR.PATCH[-rc.N].REVISION`:

| Tag                          | Where it lives           | Channel  | `PRE_RELEASE` |
| ---------------------------- | ------------------------ | -------- | ------------- |
| `2.1.0.2281`                 | `release/2.1`            | `stable` | `false`       |
| `2.1.1.2284`                 | `release/2.1`            | `stable` | `false`       |
| `2.1.0-rc.1.2280`            | `main`                   | `main`   | `true`        |

- `REVISION` is `git rev-list --count HEAD` at the tagged commit. It is unique
  **within its branch**, not globally. The OSS channel prefix prevents
  collisions between branches that happen to land on the same revision number.
- Stable tags must be on a `release/X.Y` branch.
- RC tags must be on `main`.
- The CI workflow `.github/workflows/build-release.yml` enforces both of these
  via a branch-containment guard. A tag on a feature branch will refuse to
  build.

---

## Version property file

`app/src/desktopMain/resources/crosspaste-version.properties` holds the active
version for the branch it lives on. The CI script
`.github/scripts/validateAndUpdateVersion.js` cross-checks the tag against this
file and fails the build on mismatch.

| Branch         | What `version=` should be                                             |
| -------------- | --------------------------------------------------------------------- |
| `main`         | The **next minor** that will be cut from `main` (e.g. `2.2.0`). Bumped immediately after cutting a release branch. |
| `release/X.Y`  | The latest `X.Y.Z` that has been (or is about to be) tagged on this branch. Bumped on the release branch before each patch tag. |

Mismatches between this file and the tag are a hard error — never patch around
them, fix the file.

---

## OSS / update channels

| Channel  | OSS prefix                                       | Who consumes it                         |
| -------- | ------------------------------------------------ | --------------------------------------- |
| `stable` | `oss://crosspaste-desktop/stable/X.Y.Z.REV/`     | End users on the default update channel |
| `main`   | `oss://crosspaste-desktop/main/X.Y.Z-rc.N.REV/`  | Users opted in to pre-release channel   |

The update manifest must serve these channels separately. A user on `stable`
must never be auto-updated to an RC build.

---

## Cutting a minor release (X.Y.0)

Run from a clean `main`:

```bash
./cut-release.sh 2.1.0
```

This script:

1. Verifies you are on `main`, the working tree is clean, and `main` is in sync
   with `origin/main`.
2. Verifies `crosspaste-version.properties` contains `version=2.1.0`.
3. Creates `release/2.1` from `main` HEAD and pushes it.
4. On `main`, creates a branch `chore/bump-to-2.2.0` that bumps the properties
   file to `2.2.0`, and pushes it. You must then open a PR and merge that
   branch into `main`.

After the script prints its summary:

```bash
git checkout release/2.1
REV=$(git rev-list --count HEAD)
git tag 2.1.0.$REV
git push origin 2.1.0.$REV
```

The push triggers `build-release.yml`. When it succeeds, the artifacts are at
`oss://crosspaste-desktop/stable/2.1.0.$REV/`.

Finally, append a row to `doc/MOBILE_COMPAT.md` describing any `commonMain`
public-API changes since the previous tag.

---

## Cutting a patch release (X.Y.Z)

Patches happen on the existing `release/X.Y` branch. Assume `release/2.1`
already exists and you want `2.1.1`.

```bash
git checkout release/2.1
git pull --ff-only

# Cherry-pick the fix(es) from main, one at a time, into release/2.1:
git cherry-pick <sha-on-main>

# Bump the property file (this is manual on purpose — only you can decide
# whether a given set of cherry-picks deserves a patch bump):
#   version=2.1.1
$EDITOR app/src/desktopMain/resources/crosspaste-version.properties
git add app/src/desktopMain/resources/crosspaste-version.properties
git commit -m ":construction_worker: bump release/2.1 to 2.1.1"
git push origin release/2.1

REV=$(git rev-list --count HEAD)
git tag 2.1.1.$REV
git push origin 2.1.1.$REV
```

If a fix is needed only on the release branch (very rare — usually you want it
on `main` too), write the fix on `release/2.1` directly and immediately
cherry-pick it onto `main`. The trunk-first rule prevents drift.

---

## Pre-release / RC flow

Used when you want to dogfood a build that includes a big feature before
committing it to a stable release.

```bash
# On main, with the big feature's flag turned ON:
git checkout main
git pull --ff-only

REV=$(git rev-list --count HEAD)
git tag 2.1.0-rc.1.$REV
git push origin 2.1.0-rc.1.$REV
```

The CI workflow detects the `-rc.` segment, sets `PRE_RELEASE=true`, and
uploads to `oss://crosspaste-desktop/main/2.1.0-rc.1.$REV/`. The update
manifest serves this only to the `main` channel.

Iterate `rc.2`, `rc.3` … by pushing further commits to `main` and re-tagging.

When you are ready to ship `2.1.0` for real, follow the
[minor release flow](#cutting-a-minor-release-xy0). The RC tags stay as
historical artifacts on `main`.

---

## Mobile sync

Mobile projects copy `commonMain` from this repo. The contract is:

- Mobile **only** syncs from a tagged commit, never from `main` HEAD.
- After every stable release, append a row to `doc/MOBILE_COMPAT.md`:
  - The tag (e.g. `2.1.0.2281`).
  - Any breaking `commonMain` API changes since the previous stable tag.
  - Whether mobile has been updated to consume it.
- Commits that change `commonMain` public API in a breaking way should use the
  `:boom:` emoji prefix in their commit message, so the changes are easy to
  audit when assembling the compatibility row.

When in doubt about whether a `commonMain` change is breaking for mobile,
assume it is and document it. False positives cost nothing; missed breaks cost
a mobile release.

---

## Rollback

If a stable release turns out to be broken:

1. **Do not delete the tag.** Tagged artifacts are immutable history. Users
   with the bad version already have it; pulling the tag only hides the
   problem.
2. Fix the bug on `main`, cherry-pick onto `release/X.Y`, and ship a patch
   following [Cutting a patch release](#cutting-a-patch-release-xyz).
3. If the bug is severe enough that users should not stay on the bad version,
   mark the bad version as withdrawn in the update manifest so the auto-updater
   forces a jump to the patch release.

---

## Checklist

Before tagging a stable release:

- [ ] All in-flight large features are either complete or guarded by a flag
      that is off in the release build.
- [ ] `./gradlew app:desktopTest` passes locally on the release branch.
- [ ] `CHANGELOG.md` updated with the release notes.
- [ ] `crosspaste-version.properties` on the release branch matches the tag
      version.
- [ ] `MOBILE_COMPAT.md` has a row for this tag (filled in after the build
      succeeds).
