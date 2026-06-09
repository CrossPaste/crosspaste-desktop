#!/usr/bin/env python3
"""Generate the portable Windows shortcut shipped at the root of the zip.

The Conveyor Windows zip places the launcher at ``bin\\CrossPaste.exe``, so a
freshly extracted zip forces users to dig into ``bin`` to start the app. This
script produces a single static ``CrossPaste.lnk`` that lives at the zip root
and points at ``bin\\CrossPaste.exe`` via a RELATIVE path, so it keeps working
no matter where the user extracts (or later moves) the folder.

How the shortcut resolves on Windows:
  * It carries a sentinel ABSOLUTE target (``C:\\CrossPaste\\bin\\CrossPaste.exe``)
    only so Explorer treats the ``.lnk`` as a valid, double-clickable shortcut.
    That path almost never exists on a user's machine.
  * It also carries a RELATIVE path (``.\\bin\\CrossPaste.exe``). When the
    absolute target is missing, the shell falls back to the relative path,
    resolved against the directory that contains the ``.lnk`` -- i.e. the zip
    root -- and finds ``<extract-dir>\\bin\\CrossPaste.exe``.

The ``.lnk`` is version-independent (the relative target never changes), so we
generate it once and COMMIT it to ``app/script/CrossPaste.lnk``; conveyor.conf
maps it to the zip root. Re-run this only if the layout/target changes.

Requirements: ``pip install pylnk3`` (only needed to regenerate, NOT in CI).
Usage: ``python3 scripts/generate-windows-lnk.py``

NOTE: This file format is documented (MS-SHLLINK) and generating it needs no
Windows host, but the actual click-to-launch behaviour MUST be validated on
real Windows (Win10/Win11, different drive letters, after moving the folder)
before relying on it -- the relative-fallback semantics are the uncertain part.
"""

from datetime import datetime
from pathlib import Path

import pylnk3

# Sentinel absolute target: present only so Explorer accepts the .lnk as valid.
# Pointing it at a plausible install dir is a bonus -- if a user really extracts
# to C:\CrossPaste the absolute path resolves directly; otherwise it fails and
# the relative path below takes over.
SENTINEL_ABSOLUTE = r"C:\CrossPaste\bin\CrossPaste.exe"

# Relative to the .lnk's own directory (the zip root after extraction).
RELATIVE_TARGET = r".\bin\CrossPaste.exe"

# Fixed timestamp so the committed binary is reproducible across regenerations.
FIXED_TIME = datetime(2024, 1, 1, 0, 0, 0)

OUTPUT = Path(__file__).resolve().parent.parent / "app" / "script" / "CrossPaste.lnk"


def main() -> None:
    lnk = pylnk3.for_file(
        SENTINEL_ABSOLUTE,
        description="CrossPaste",
        window_mode="Normal",
        # work_dir intentionally omitted: the Conveyor launcher locates its
        # runtime relative to the exe, and a relative work_dir would point cwd
        # at a non-existent path. icon intentionally omitted: a relative icon
        # path does not render; with none set, Explorer uses the resolved
        # target exe's own icon.
    )
    lnk.relative_path = RELATIVE_TARGET  # auto-sets the HasRelativePath flag
    lnk.creation_time = FIXED_TIME
    lnk.access_time = FIXED_TIME
    lnk.modification_time = FIXED_TIME

    # The IDList path segments also embed wall-clock timestamps (from the build
    # host); pin them so the committed binary is byte-for-byte reproducible.
    for item in lnk.shell_item_id_list.items:
        for attr in ("created", "modified", "accessed"):
            if hasattr(item, attr):
                setattr(item, attr, FIXED_TIME)

    lnk.save(str(OUTPUT))
    print(f"wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")
    print(f"  relative target: {RELATIVE_TARGET}")
    print(f"  sentinel absolute: {SENTINEL_ABSOLUTE}")


if __name__ == "__main__":
    main()
