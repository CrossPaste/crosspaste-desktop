package com.crosspaste.log

expect fun initLogger(logPath: String): CrossPasteLogger

interface CrossPasteLogger {
    val logLevel: String
    val logPath: String
    val loggerDebugPackages: String?
}
