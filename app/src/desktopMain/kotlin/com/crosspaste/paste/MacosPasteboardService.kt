package com.crosspaste.paste

import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.getControlUtils
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import kotlin.time.Duration.Companion.milliseconds

class MacosPasteboardService(
    override val appWindowManager: DesktopAppWindowManager,
    override val configManager: CommonConfigManager,
    override val currentPaste: CurrentPaste,
    override val notificationManager: NotificationManager,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
    override val pasteReleaseService: PasteReleaseService,
    override val soundService: SoundService,
    override val sourceExclusionService: DesktopSourceExclusionService,
    private val syncManager: SyncManager,
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

    private fun run(): Job =
        serviceScope.launch(CoroutineName("MacPasteboardService")) {
            var firstRead = true
            while (isActive) {
                runCatching {
                    pollPasteboard(firstRead)
                    firstRead = false
                }.onFailure { e ->
                    logger.error(e) { "Failed to consume transferable" }
                }
                delay(280L.milliseconds)
            }
        }

    /**
     * One poll of the pasteboard change count. Decides whether the change is
     * worth reading at all (not our own write, not a password-manager secret,
     * not stale content from before launch) before touching the contents.
     */
    private suspend fun CoroutineScope.pollPasteboard(firstRead: Boolean) {
        val remote = IntByReference()
        val isCrossPaste = IntByReference()
        val isConcealed = IntByReference()
        val currentChangeCount =
            MacosApi.INSTANCE.getPasteboardChangeCount(changeCount, remote, isCrossPaste, isConcealed)
        if (changeCount == currentChangeCount) return

        logger.info { "currentChangeCount $currentChangeCount changeCount $changeCount" }
        val config = configManager.getCurrentConfig()
        val firstChange = firstRead && changeCount == config.lastPasteboardChangeCount
        changeCount = currentChangeCount

        when {
            firstChange && config.enableSkipPreLaunchPasteboardContent -> {
                logger.debug { "Ignoring prior pasteboard" }
            }
            isCrossPaste.value != 0 -> {
                logger.debug { "Ignoring crosspaste change" }
            }
            isConcealed.value != 0 -> {
                logger.debug { "Ignoring concealed pasteboard content" }
            }
            else -> {
                consumeChange(firstChange = firstChange, remote = remote.value != 0)
            }
        }
    }

    private suspend fun CoroutineScope.consumeChange(
        firstChange: Boolean,
        remote: Boolean,
    ) {
        val source = resolveSource(firstChange)
        if (sourceExclusionService.isExcluded(source)) {
            logger.debug { "Ignoring excluded source: $source" }
            return
        }

        val contents =
            controlUtils.exponentialBackoffUntilValid(
                initTime = 20L,
                maxTime = 1000L,
                isValidResult = ::isValidContents,
            ) {
                getPasteboardContentsBySafe()
            }
        if (contents == null || contents == ownerTransferable) return

        ownerTransferable = contents
        // Content that arrived via Universal Clipboard is already on every Apple
        // device; only push it to the others.
        val targetAppInstanceIds = if (remote) nonAppleSyncTargets() else null
        launch(CoroutineName("MacPasteboardServiceConsumer")) {
            pasteConsumer.consume(
                DesktopReadTransferable(contents),
                PasteSourceContext(
                    source = source,
                    remote = false,
                    targetAppInstanceIds = targetAppInstanceIds,
                ),
            )
        }
    }

    private suspend fun resolveSource(firstChange: Boolean): String? {
        val source =
            controlUtils
                .ensureMinExecutionTime(delayTime = 20) {
                    appWindowManager.getCurrentActiveAppName()
                }.getOrNull()
        // https://github.com/CrossPaste/crosspaste-desktop/issues/1874
        // If it is the first time to read the pasteboard content and the source is CrossPaste
        // we should ignore its source
        return if (firstChange && source == AppName) null else source
    }

    private fun nonAppleSyncTargets(): Set<String> =
        syncManager
            .getSyncHandlers()
            .filterValues { handler -> !handler.currentSyncRuntimeInfo.platform.isApple() }
            .keys

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
