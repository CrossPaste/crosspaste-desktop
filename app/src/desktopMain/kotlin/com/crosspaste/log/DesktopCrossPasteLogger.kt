package com.crosspaste.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getSystemProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DesktopCrossPasteLogger(
    override val logPath: String,
    private val configManager: DesktopConfigManager,
) : CrossPasteLogger {

    override lateinit var logLevel: String
    override val loggerDebugPackages: String?

    var rootLogger: ch.qos.logback.classic.Logger? = null

    var jThemeLogger: ch.qos.logback.classic.Logger? = null
    var jmDNSLogger: ch.qos.logback.classic.Logger? = null

    init {
        val systemProperty = getSystemProperty()
        val config = configManager.getCurrentConfig()
        systemProperty.getOption("loggerLevel")?.let {
            logLevel = it
            if (logLevel == "debug" && !config.enableDebugMode) {
                configManager.updateConfig("enableDebugMode", true)
            } else if (logLevel != "debug" && config.enableDebugMode) {
                configManager.updateConfig("enableDebugMode", false)
            }
        } ?: run {
            logLevel =
                if (config.enableDebugMode) {
                    "debug"
                } else {
                    "info"
                }
        }

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

        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        rootLogger?.level = getLevel(logLevel)
        rootLogger?.addAppender(rollingFileAppender)

        jThemeLogger = context.getLogger("com.jthemedetecor") as ch.qos.logback.classic.Logger
        jThemeLogger?.level = Level.OFF
        jmDNSLogger = context.getLogger("javax.jmdns") as ch.qos.logback.classic.Logger
        jmDNSLogger?.level = Level.OFF

        loggerDebugPackages = systemProperty.getOption("loggerDebugPackages")

        loggerDebugPackages?.let { debugPackages ->
            for (packageName in debugPackages.split(",")) {
                val logger = context.getLogger(packageName) as ch.qos.logback.classic.Logger
                logger.level = Level.DEBUG
            }
        }
    }

    override fun updateRootLogLevel(logLevel: String) {
        val level = getLevel(logLevel)
        if (level == Level.DEBUG) {
            configManager.updateConfig("enableDebugMode", true)
            jThemeLogger?.level = Level.DEBUG
            jmDNSLogger?.level = Level.DEBUG
        } else {
            configManager.updateConfig("enableDebugMode", false)
            jThemeLogger?.level = Level.OFF
            jmDNSLogger?.level = Level.OFF
        }
        rootLogger?.level = getLevel(logLevel)
    }

    private fun getLevel(logLevel: String): Level {
        return when (logLevel) {
            "trace" -> Level.TRACE
            "debug" -> Level.DEBUG
            "info" -> Level.INFO
            "warn" -> Level.WARN
            "error" -> Level.ERROR
            else -> Level.INFO
        }
    }
}
