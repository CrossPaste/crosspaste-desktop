package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.toKString
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.getenv
import platform.posix.mkdtemp
import platform.posix.system

/** Default editor when neither VISUAL nor EDITOR is set. */
const val FALLBACK_EDITOR = "vi"

/** The narrow getenv is correct on POSIX, where the environment is byte-oriented. */
@OptIn(ExperimentalForeignApi::class)
fun readPlatformEnv(name: String): String? = getenv(name)?.toKString()

/**
 * Creates a fresh private directory for an edit round-trip via mkdtemp(3):
 * atomically created with a random name and mode 0700. The directory (not the
 * file inside) carries the protection — no other local user can read the
 * clipboard content placed in it regardless of the file's umask-derived mode,
 * no symlink can be planted at a predictable path, and concurrent edits never
 * share a file. Returns null when the directory cannot be created.
 */
@OptIn(ExperimentalForeignApi::class)
fun createEditTempDir(): Path? {
    val base = readPlatformEnv("TMPDIR")?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: "/tmp"
    val template = "$base/crosspaste-edit-XXXXXX".cstr
    return kotlinx.cinterop.memScoped {
        mkdtemp(template.getPointer(this))?.toKString()?.toPath()
    }
}

/**
 * Launches the editor through the shell (system(3)), inheriting the terminal
 * so full-screen editors work. $VISUAL/$EDITOR may carry arguments
 * ("code --wait") so the editor value is passed to the shell as-is; the file
 * path is single-quoted so sh performs no `$`, backtick, or glob expansion on
 * it. Returns the raw wait status: only zero is treated as success by
 * callers, so the WEXITSTATUS encoding (including deaths by signal, which a
 * decoded exit code would mask as 0) never turns a failed editor session into
 * a false success.
 */
@OptIn(ExperimentalForeignApi::class)
fun runEditor(
    editor: String,
    filePath: String,
): Int = system("$editor ${shellQuotePath(filePath)}")

/**
 * 'single-quoted' for sh: nothing expands inside single quotes; an embedded
 * quote uses the close–escape–reopen idiom ('\'').
 */
internal fun shellQuotePath(path: String): String = "'" + path.replace("'", "'\\''") + "'"
