package com.crosspaste.cli.commands

import com.crosspaste.cli.platform.AppLauncher
import com.crosspaste.cli.platform.AppReadinessChecker

class AppAutoStarter(
    private val launcher: AppLauncher,
    private val readinessChecker: AppReadinessChecker,
) {

    suspend fun startAndWait(echo: (message: String, isError: Boolean) -> Unit): Boolean {
        echo("CrossPaste is not running. Starting CrossPaste...", false)

        val launched = launcher.launch()
        if (!launched) {
            echo("Failed to start CrossPaste. Please start it manually.", true)
            return false
        }

        echo("Waiting for CrossPaste to become ready...", false)
        val ready = readinessChecker.waitForAppReady()
        if (!ready) {
            echo("Timed out waiting for CrossPaste to become ready.", true)
            return false
        }

        echo("CrossPaste is now running.", false)
        return true
    }
}
