package com.clipevery.clip

import com.clipevery.app.AppWindowManager
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.utils.DesktopControlUtils.ensureMinExecutionTime
import com.clipevery.utils.DesktopControlUtils.exponentialBackoffUntilValid
import com.clipevery.utils.cpuDispatcher
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
) : ClipboardService {
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
                    val isClipevery = IntByReference()
                    MacosApi.INSTANCE.getClipboardChangeCount(changeCount, remote, isClipevery)
                        .let { currentChangeCount ->
                            if (changeCount != currentChangeCount) {
                                logger.info { "currentChangeCount $currentChangeCount changeCount $changeCount" }
                                changeCount = currentChangeCount
                                if (isClipevery.value != 0) {
                                    logger.debug { "Ignoring clipevery change" }
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
        if (configManager.config.enableClipboardListening) {
            if (job?.isActive != true) {
                job = run()
            }
        }
    }

    @Synchronized
    override fun stop() {
        job?.cancel()
        configManager.updateConfig { it.copy(lastClipboardChangeCount = changeCount) }
    }

    @Synchronized
    override fun toggle() {
        val enableClipboardListening = configManager.config.enableClipboardListening
        configManager.updateConfig { it.copy(enableClipboardListening = !enableClipboardListening) }
        if (!enableClipboardListening) {
            start()
        } else {
            stop()
        }
    }
}
