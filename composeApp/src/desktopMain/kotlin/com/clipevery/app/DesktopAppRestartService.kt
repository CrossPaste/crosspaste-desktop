package com.clipevery.app

import com.clipevery.platform.currentPlatform

object DesktopAppRestartService : AppRestartService {

    private val currentPlatform = currentPlatform()

    private val appRestartService: AppRestartService =
        if (currentPlatform.isMacos()) {
            MacAppRestartService()
        } else if (currentPlatform.isWindows()) {
            WindowsAppRestartService()
        } else if (currentPlatform.isLinux()) {
            LinuxAppRestartService()
        } else {
            throw IllegalStateException("Unsupported platform: $currentPlatform")
        }

    override fun restart(exitApplication: () -> Unit) {
        if (AppEnv.isProduction()) {
            appRestartService.restart(exitApplication)
        } else {
            exitApplication()
        }
    }
}

class MacAppRestartService : AppRestartService {

    companion object {
        private const val SCRIPT = "restart.sh"
    }

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        Runtime.getRuntime().exec("sh $SCRIPT $pid")
        exitApplication()
    }
}

class WindowsAppRestartService : AppRestartService {

    companion object {
        private const val SCRIPT = "restart.bat"
    }

    override fun restart(exitApplication: () -> Unit) {
        val pid = ProcessHandle.current().pid()
        Runtime.getRuntime().exec("sh $SCRIPT $pid")
        exitApplication()
    }
}

class LinuxAppRestartService : AppRestartService {
    override fun restart(exitApplication: () -> Unit) {
        TODO("Not yet implemented")
    }
}
