package com.crosspaste.paste

import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.utils.cpuDispatcher
import com.crosspaste.utils.getControlUtils
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

class MacosPasteboardService(
    override val appWindowManager: DesktopAppWindowManager,
    override val pasteRealm: PasteRealm,
    override val configManager: ConfigManager,
    override val currentPaste: CurrentPaste,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
) : AbstractPasteboardService() {
    override val logger: KLogger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private var changeCount = configManager.config.lastPasteboardChangeCount

    @Volatile
    override var owner = false

    @Volatile
    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override val pasteboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

    private val serviceScope = CoroutineScope(cpuDispatcher + SupervisorJob())

    private var job: Job? = null

    init {
        serviceScope.launch {
            for (task in pasteboardChannel) {
                task()
            }
        }
    }

    private fun run(): Job {
        return serviceScope.launch(CoroutineName("MacPasteboardService")) {
            while (isActive) {
                try {
                    val remote = IntByReference()
                    val isCrossPaste = IntByReference()
                    MacosApi.INSTANCE.getPasteboardChangeCount(changeCount, remote, isCrossPaste)
                        .let { currentChangeCount ->
                            if (changeCount != currentChangeCount) {
                                logger.info { "currentChangeCount $currentChangeCount changeCount $changeCount" }
                                val firstChange = changeCount == configManager.config.lastPasteboardChangeCount
                                changeCount = currentChangeCount
                                if (isCrossPaste.value != 0) {
                                    logger.debug { "Ignoring crosspaste change" }
                                } else {
                                    var source: String? =
                                        controlUtils.ensureMinExecutionTime(delayTime = 20) {
                                            appWindowManager.getCurrentActiveAppName()
                                        }

                                    // https://github.com/CrossPaste/crosspaste-desktop/issues/1874
                                    // If it is the first time to read the pasteboard content and the source is CrossPaste
                                    // we should ignore its source
                                    if (firstChange && source == AppName) {
                                        source = null
                                    }

                                    val contents =
                                        controlUtils.exponentialBackoffUntilValid(
                                            initTime = 20L,
                                            maxTime = 1000L,
                                            isValidResult = ::isValidContents,
                                        ) {
                                            getPasteboardContentsBySafe()
                                        }
                                    if (contents != ownerTransferable) {
                                        contents?.let {
                                            ownerTransferable = it
                                            launch(CoroutineName("MacPasteboardServiceConsumer")) {
                                                val pasteTransferable = DesktopReadTransferable(it)
                                                pasteConsumer.consume(pasteTransferable, source, remote.value != 0)
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
        configManager.updateConfig("lastPasteboardChangeCount", changeCount)
    }
}
