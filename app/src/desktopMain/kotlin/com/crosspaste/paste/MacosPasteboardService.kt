package com.crosspaste.paste

import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.getControlUtils
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

class MacosPasteboardService(
    override val appWindowManager: DesktopAppWindowManager,
    override val configManager: CommonConfigManager,
    override val currentPaste: CurrentPaste,
    override val notificationManager: NotificationManager,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
    override val pasteDao: PasteDao,
    override val soundService: SoundService,
    override val sourceExclusionService: SourceExclusionService,
) : AbstractPasteboardService() {
    override val logger: KLogger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private var changeCount = configManager.getCurrentConfig().lastPasteboardChangeCount

    @Volatile
    override var owner = false

    @Volatile
    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private var job: Job? = null

    init {
        startRemotePasteboardListener()
    }

    private fun run(): Job {
        return serviceScope.launch(CoroutineName("MacPasteboardService")) {
            var firstRead = true
            while (isActive) {
                runCatching {
                    val remote = IntByReference()
                    val isCrossPaste = IntByReference()
                    MacosApi.INSTANCE
                        .getPasteboardChangeCount(changeCount, remote, isCrossPaste)
                        .let { currentChangeCount ->
                            if (changeCount != currentChangeCount) {
                                logger.info { "currentChangeCount $currentChangeCount changeCount $changeCount" }
                                val firstChange =
                                    firstRead &&
                                        changeCount ==
                                        configManager
                                            .getCurrentConfig()
                                            .lastPasteboardChangeCount

                                changeCount = currentChangeCount

                                if (firstChange &&
                                    configManager
                                        .getCurrentConfig()
                                        .enableSkipPreLaunchPasteboardContent
                                ) {
                                    logger.debug { "Ignoring prior pasteboard" }
                                    return@let
                                }

                                if (isCrossPaste.value != 0) {
                                    logger.debug { "Ignoring crosspaste change" }
                                } else {
                                    var source: String? =
                                        controlUtils
                                            .ensureMinExecutionTime(delayTime = 20) {
                                                appWindowManager.getCurrentActiveAppName()
                                            }.getOrNull()

                                    // https://github.com/CrossPaste/crosspaste-desktop/issues/1874
                                    // If it is the first time to read the pasteboard content and the source is CrossPaste
                                    // we should ignore its source
                                    if (firstChange && source == AppName) {
                                        source = null
                                    }

                                    if (sourceExclusionService.isExcluded(source)) {
                                        logger.debug { "Ignoring excluded source: $source" }
                                        return@let
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
                    firstRead = false
                }.onFailure { e ->
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
