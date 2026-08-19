package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.wcstr
import okio.Path
import okio.Path.Companion.toPath
import platform.posix._wgetenv
import platform.posix._wsystem
import platform.posix.time
import platform.windows.CreateDirectoryW
import platform.windows.GetCurrentProcessId

/** Default editor when neither VISUAL nor EDITOR is set. */
const val FALLBACK_EDITOR = "notepad"

/**
 * The narrow getenv would return values re-encoded through the ANSI code
 * page, garbling non-ASCII content (e.g. TEMP under a Chinese user profile)
 * before it ever reaches the wide-character APIs; _wgetenv reads the native
 * UTF-16 environment directly.
 */
@OptIn(ExperimentalForeignApi::class)
fun readPlatformEnv(name: String): String? = memScoped { _wgetenv(name.wcstr.ptr)?.toKStringFromUtf16() }

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

/**
 * Runs the editor via cmd.exe using the wide-character _wsystem: the ANSI
 * system() would push our UTF-8 Kotlin string through the OEM code page and
 * garble non-ASCII paths — see the cmd.exe interop pitfalls in CLAUDE.md.
 * cmd has no single quotes, so the path is double-quoted (covers spaces);
 * the CLI-generated dir and file names contain no quotes or `%`, so cmd's
 * %VAR% expansion cannot trigger inside them (a literal `%` in %TEMP% itself
 * would be exotic enough to accept). Returns the shell's exit status.
 */
@OptIn(ExperimentalForeignApi::class)
fun runEditor(
    editor: String,
    filePath: String,
): Int = memScoped { _wsystem("$editor \"$filePath\"".wcstr.ptr) }
