package com.crosspaste.bootstrap

import java.awt.GraphicsEnvironment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * The exit for a startup that cannot continue. It must work before the file
 * logger exists (a packaged app has no console, so a stack trace on stderr
 * alone is invisible), so it writes with plain java.io and tells a GUI user
 * where to look.
 */
object StartupFailure {

    const val FILE_NAME = "startup-failure.log"

    fun exit(
        error: Throwable,
        logDir: File,
        headless: Boolean,
    ): Nothing {
        val logFile = writeLog(error, logDir)
        System.err.println("CrossPaste failed to start")
        error.printStackTrace()
        if (!headless && !GraphicsEnvironment.isHeadless()) {
            runCatching {
                JOptionPane.showMessageDialog(
                    null,
                    "CrossPaste failed to start.\n" +
                        (logFile?.let { "Details were written to:\n${it.absolutePath}" } ?: (error.message ?: "")),
                    "CrossPaste",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }
        exitProcess(1)
    }

    /** Appends [error] to the failure log in [logDir]; null if even that fails. */
    fun writeLog(
        error: Throwable,
        logDir: File,
    ): File? =
        runCatching {
            logDir.mkdirs()
            val stackTrace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            File(logDir, FILE_NAME).apply {
                appendText("=== ${LocalDateTime.now()} ===\n$stackTrace\n")
            }
        }.getOrNull()
}
