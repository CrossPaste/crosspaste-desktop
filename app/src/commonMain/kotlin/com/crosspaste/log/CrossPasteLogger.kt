package com.crosspaste.log

interface CrossPasteLogger {
    val logLevel: String
    val logPath: String
    val loggerDebugPackages: String?

    fun updateRootLogLevel(logLevel: String)
}
