package com.crosspaste.net.exception

import java.net.ConnectException

class DesktopExceptionHandler : ExceptionHandler() {
    override fun isConnectionRefused(e: Throwable): Boolean {
        return e is ConnectException
    }
}
