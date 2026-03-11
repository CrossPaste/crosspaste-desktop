package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix._pclose
import platform.posix._popen
import platform.posix.fputs

@OptIn(ExperimentalForeignApi::class)
fun pipeToCommand(
    command: String,
    text: String,
) {
    val pipe = _popen(command, "w") ?: error("Failed to run '$command'. Is it installed?")
    fputs(text, pipe)
    val exitCode = _pclose(pipe)
    if (exitCode != 0) {
        error("Clipboard command '$command' failed (exit $exitCode)")
    }
}
