package com.crosspaste.net.exception

import java.net.ConnectException

class DesktopExceptionHandler : ExceptionHandler() {
    override fun isPortAlreadyInUse(e: Throwable): Boolean {
        return e.message?.lowercase()?.contains("already in use") == true
    }

    override fun isConnectionRefused(e: Throwable): Boolean {
        return e is ConnectException
    }
}
