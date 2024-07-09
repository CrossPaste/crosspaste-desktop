package com.crosspaste.utils

expect fun getExceptionUtils(): ExceptionUtils

interface ExceptionUtils {

    fun isConnectionRefused(e: Throwable): Boolean
}
