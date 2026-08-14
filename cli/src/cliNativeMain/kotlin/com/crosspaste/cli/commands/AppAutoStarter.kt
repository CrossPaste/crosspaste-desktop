package com.crosspaste.cli.commands

import com.crosspaste.cli.platform.AppLauncher
import com.crosspaste.cli.platform.AppReadinessChecker

/**
 * Launches the desktop app and waits for its CLI endpoint to come up.
 * Progress messages are emitted through [echo] so callers can keep them
 * out of stdout (pipes must only receive command output).
 */
class AppAutoStarter(
    private val launcher: AppLauncher,
    private val readinessChecker: AppReadinessChecker,
) {

    suspend fun startAndWait(echo: (message: String) -> Unit): Boolean {
        echo("Starting CrossPaste...")

        val launched = launcher.launch()
        if (!launched) {
            echo("Failed to start CrossPaste. Please start it manually.")
            return false
        }

        echo("Waiting for CrossPaste to become ready...")
        val ready = readinessChecker.waitForAppReady()
        if (!ready) {
            echo("Timed out waiting for CrossPaste to become ready. Please check the application manually.")
            return false
        }

        echo("CrossPaste is now running.")
        return true
    }
}
