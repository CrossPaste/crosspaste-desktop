package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.system

/** Default editor when neither VISUAL nor EDITOR is set. */
const val FALLBACK_EDITOR = "vi"

/**
 * Runs [command] through the shell (system(3)), inheriting the terminal so
 * full-screen editors work. Returns the raw wait status: only zero is treated
 * as success by callers, so the WEXITSTATUS encoding (including deaths by
 * signal, which a decoded exit code would mask as 0) never turns a failed
 * editor session into a false success.
 */
@OptIn(ExperimentalForeignApi::class)
fun runEditorCommand(command: String): Int = system(command)
