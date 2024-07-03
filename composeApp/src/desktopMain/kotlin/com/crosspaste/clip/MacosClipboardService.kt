package com.crosspaste.clip

import com.crosspaste.app.AppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.utils.DesktopControlUtils.ensureMinExecutionTime
import com.crosspaste.utils.DesktopControlUtils.exponentialBackoffUntilValid
import com.crosspaste.utils.cpuDispatcher
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

class MacosClipboardService(
    override val appWindowManager: AppWindowManager,
    override val clipDao: ClipDao,
    override val configManager: ConfigManager,
    override val clipConsumer: TransferableConsumer,
    override val clipProducer: TransferableProducer,
) : AbstractClipboardService() {
    override val logger: KLogger = KotlinLogging.logger {}

    private var changeCount = configManager.config.lastClipboardChangeCount

    @Volatile
    override var owner = false

    @Volatile
    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override val clipboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

    private val serviceScope = CoroutineScope(cpuDispatcher + SupervisorJob())

    private var job: Job? = null

    init {
        serviceScope.launch {
            for (task in clipboardChannel) {
                task()
            }
        }
    }

    private fun run(): Job {
        return serviceScope.launch(CoroutineName("MacClipboardService")) {
            while (isActive) {
                try {
                    val remote = IntByReference()
                    val isCrossPaste = IntByReference()
                    MacosApi.INSTANCE.getClipboardChangeCount(changeCount, remote, isCrossPaste)
                        .let { currentChangeCount ->
                            if (changeCount != currentChangeCount) {
                                logger.info { "currentChangeCount $currentChangeCount changeCount $changeCount" }
                                changeCount = currentChangeCount
                                if (isCrossPaste.value != 0) {
                                    logger.debug { "Ignoring crosspaste change" }
                                } else {
                                    val source =
                                        ensureMinExecutionTime(delayTime = 20) {
                                            appWindowManager.getCurrentActiveAppName()
                                        }

                                    val contents =
                                        exponentialBackoffUntilValid(
                                            initTime = 20L,
                                            maxTime = 1000L,
                                            isValidResult = ::isValidContents,
                                        ) {
                                            getClipboardContentsBySafe()
                                        }
                                    if (contents != ownerTransferable) {
                                        contents?.let {
                                            ownerTransferable = it
                                            launch(CoroutineName("MacClipboardServiceConsumer")) {
                                                clipConsumer.consume(it, source, remote.value != 0)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to consume transferable" }
                }
                delay(280L)
            }
        }
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }

    @Synchronized
    override fun start() {
        if (job?.isActive != true) {
            job = run()
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        configManager.updateConfig { it.copy(lastClipboardChangeCount = changeCount) }
    }
}