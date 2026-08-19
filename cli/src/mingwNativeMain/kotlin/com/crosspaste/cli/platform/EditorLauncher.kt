package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.wcstr
import platform.posix._wsystem

/** Default editor when neither VISUAL nor EDITOR is set. */
const val FALLBACK_EDITOR = "notepad"

/**
 * Runs [command] via cmd.exe using the wide-character _wsystem: the ANSI
 * system() would push our UTF-8 Kotlin string through the OEM code page and
 * garble non-ASCII paths (e.g. a temp dir under a Chinese user profile) — see
 * the cmd.exe interop pitfalls in CLAUDE.md. Returns the shell's exit status.
 */
@OptIn(ExperimentalForeignApi::class)
fun runEditorCommand(command: String): Int = memScoped { _wsystem(command.wcstr.ptr) }
