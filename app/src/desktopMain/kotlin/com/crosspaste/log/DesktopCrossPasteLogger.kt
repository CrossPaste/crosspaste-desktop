package com.crosspaste.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import ch.qos.logback.core.util.StatusPrinter2
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DesktopCrossPasteLogger(
    override val logPath: String,
    configManager: DesktopConfigManager,
    loggerLevelScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) : CrossPasteLogger {

    private val context = LoggerFactory.getILoggerFactory() as LoggerContext

    private val rootLogger: ch.qos.logback.classic.Logger = context.getLogger(Logger.ROOT_LOGGER_NAME)

    private val jmDNSLogger: ch.qos.logback.classic.Logger = context.getLogger("javax.jmdns")

    init {
        val encoder =
            PatternLayoutEncoder().apply {
                this.context = this@DesktopCrossPasteLogger.context
                this.pattern = "%date %level [%thread] %logger{10} [%file:%line] %msg%n"
                start()
            }

        val rollingFileAppender =
            RollingFileAppender<ILoggingEvent>().apply {
                this.context = this@DesktopCrossPasteLogger.context
                this.file = logPath
                this.encoder = encoder
                this.isImmediateFlush = true
            }

        val rollingPolicy =
            TimeBasedRollingPolicy<ILoggingEvent>().apply {
                this.context = this@DesktopCrossPasteLogger.context
                this.setParent(rollingFileAppender)
                this.fileNamePattern = "$logPath.%d{yyyy-MM-dd}.log"
                this.maxHistory = 7
                this.setTotalSizeCap(FileSize(1024L * 1024 * 10))
                start()
            }

        rollingFileAppender.rollingPolicy = rollingPolicy
        rollingFileAppender.start()

        rootLogger.level = Level.INFO
        rootLogger.addAppender(rollingFileAppender)

        jmDNSLogger.level = Level.OFF
        StatusPrinter2().print(context)

        configManager.config
            .map { it.enableDebugMode }
            .distinctUntilChanged()
            .onEach { enableDebugMode ->
                val level = if (enableDebugMode) "debug" else "info"
                updateRootLogLevel(level)
            }.launchIn(loggerLevelScope)
    }

    private fun updateRootLogLevel(logLevel: String) {
        val level = getLevel(logLevel)
        if (level == Level.DEBUG) {
            jmDNSLogger.level = Level.DEBUG
        } else {
            jmDNSLogger.level = Level.OFF
        }
        rootLogger.level = level
    }

    private fun getLevel(logLevel: String): Level =
        when (logLevel) {
            "trace" -> Level.TRACE
            "debug" -> Level.DEBUG
            "info" -> Level.INFO
            "warn" -> Level.WARN
            "error" -> Level.ERROR
            else -> Level.INFO
        }
}
