package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.wcstr
import okio.Path
import okio.Path.Companion.toPath
import platform.posix._wputenv
import platform.posix._wsystem
import platform.posix.time
import platform.windows.CreateDirectoryW
import platform.windows.GetCurrentProcessId

/** Default editor when neither VISUAL nor EDITOR is set. */
const val FALLBACK_EDITOR = "notepad"

/**
 * Creates a fresh directory for an edit round-trip under %TEMP%, which is
 * already per-user and ACL-protected on Windows — the directory provides
 * uniqueness against concurrent edits, not permissions. CreateDirectoryW
 * fails when the path already exists, so the create-then-use loop is
 * race-free without a separate existence check. Returns null when no
 * directory could be created.
 */
@OptIn(ExperimentalForeignApi::class)
fun createEditTempDir(): Path? {
    val base =
        readPlatformEnv("TEMP")?.takeIf { it.isNotBlank() }
            ?: readPlatformEnv("TMP")?.takeIf { it.isNotBlank() }
            ?: return null
    val stamp = "${GetCurrentProcessId()}-${time(null)}"
    for (attempt in 0 until 100) {
        val candidate = "${base.trimEnd('\\')}\\crosspaste-edit-$stamp-$attempt"
        if (CreateDirectoryW(candidate, null) != 0) return candidate.toPath()
    }
    return null
}

/** The variable [runEditor] hands the file path to cmd through. */
internal const val EDIT_FILE_ENV = "CROSSPASTE_EDIT_FILE"

/**
 * Runs the editor via cmd.exe using the wide-character _wsystem: the ANSI
 * system() would push our UTF-8 Kotlin string through the OEM code page and
 * garble non-ASCII paths — see the cmd.exe interop pitfalls in CLAUDE.md.
 *
 * The path is never inserted into the command line: cmd expands %VAR% even
 * inside double quotes, so a literal path containing %NAME% (legal in
 * Windows paths, e.g. via a %TEMP% that contains one) would be rewritten
 * into a different path. Instead the path is exported as [EDIT_FILE_ENV] and
 * the command references %CROSSPASTE_EDIT_FILE% — cmd's single-pass
 * expansion then yields the exact literal path, whatever it contains, and
 * `"` is not a legal Windows path character so the expanded value cannot
 * break the surrounding quotes. Returns the shell's exit status.
 */
@OptIn(ExperimentalForeignApi::class)
fun runEditor(
    editor: String,
    filePath: String,
): Int =
    memScoped {
        // _wputenv updates both the CRT and the process environment block,
        // so the cmd child spawned by _wsystem inherits the variable
        if (_wputenv("$EDIT_FILE_ENV=$filePath".wcstr.ptr) != 0) return -1
        _wsystem("$editor \"%$EDIT_FILE_ENV%\"".wcstr.ptr)
    }
