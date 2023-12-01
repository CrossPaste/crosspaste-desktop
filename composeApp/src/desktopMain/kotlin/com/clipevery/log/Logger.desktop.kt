package com.clipevery.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

actual fun initLogger(logPath: String) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext

    val encoder = PatternLayoutEncoder()
    encoder.context = context
    encoder.pattern = "%date %level [%thread] %logger{10} [%file:%line] %msg%n"
    encoder.start()

    val rollingFileAppender = RollingFileAppender<ILoggingEvent>()
    rollingFileAppender.context = context
    rollingFileAppender.file = logPath
    rollingFileAppender.encoder = encoder

    val rollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>()
    rollingPolicy.context = context
    rollingPolicy.setParent(rollingFileAppender)
    rollingPolicy.fileNamePattern = "$logPath.%d{yyyy-MM-dd}.log"
    rollingPolicy.maxHistory = 30
    rollingPolicy.start()

    rollingFileAppender.rollingPolicy = rollingPolicy
    rollingFileAppender.start()

    val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    rootLogger.level = Level.DEBUG
    rootLogger.addAppender(rollingFileAppender)

    val jThemeLogger = context.getLogger("com.jthemedetecor") as ch.qos.logback.classic.Logger
    jThemeLogger.level = Level.OFF
}