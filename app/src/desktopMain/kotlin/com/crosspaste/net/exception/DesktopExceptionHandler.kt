package com.crosspaste.net.exception

import java.net.ConnectException

class DesktopExceptionHandler : ExceptionHandler() {

    private fun containsPortInUseMessage(throwable: Throwable): Boolean {
        if (isExceptionMessageContainsPortInUse(throwable)) {
            return true
        }

        throwable.cause?.let {
            if (containsPortInUseMessage(it)) {
                return true
            }
        }

        for (suppressed in throwable.suppressed) {
            if (containsPortInUseMessage(suppressed)) {
                return true
            }
        }

        return false
    }

    private fun isExceptionMessageContainsPortInUse(throwable: Throwable): Boolean {
        return throwable is java.net.BindException &&
            throwable.message?.lowercase()?.contains("already in use") == true
    }

    override fun isPortAlreadyInUse(e: Throwable): Boolean {
        return containsPortInUseMessage(e)
    }

    override fun isConnectionRefused(e: Throwable): Boolean {
        return e is ConnectException
    }
}
