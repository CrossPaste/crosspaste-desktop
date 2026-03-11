package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fputs
import platform.posix.pclose
import platform.posix.popen

@OptIn(ExperimentalForeignApi::class)
fun pipeToCommand(
    command: String,
    text: String,
) {
    val pipe = popen(command, "w") ?: error("Failed to run '$command'. Is it installed?")
    fputs(text, pipe)
    val exitCode = pclose(pipe)
    if (exitCode != 0) {
        error("Clipboard command '$command' failed (exit $exitCode)")
    }
}
