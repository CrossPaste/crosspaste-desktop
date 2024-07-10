package com.crosspaste.utils

import java.net.ConnectException

actual fun getExceptionUtils(): ExceptionUtils {
    return DesktopExceptionUtils
}

object DesktopExceptionUtils : ExceptionUtils {
    override fun isConnectionRefused(e: Throwable): Boolean {
        return e is ConnectException
    }
}
