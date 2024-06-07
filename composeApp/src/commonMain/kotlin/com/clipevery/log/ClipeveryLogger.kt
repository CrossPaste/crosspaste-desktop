package com.clipevery.log

expect fun initLogger(logPath: String): ClipeveryLogger

interface ClipeveryLogger {
    val logLevel: String
    val logPath: String
    val loggerDebugPackages: String?
}
