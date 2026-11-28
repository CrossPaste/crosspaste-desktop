package com.crosspaste.paste

import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.getControlUtils
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
    override val capturePolicy: ClipboardCapturePolicy,
    override val configManager: CommonConfigManager,
    override val currentPaste: CurrentPaste,
    override val notificationManager: NotificationManager,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
    override val pasteReleaseService: PasteReleaseService,
    override val soundService: SoundService,
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
     * One poll of the pasteboard change count. Reads the raw type list only
     * when the count moved, then hands classification, source exclusion, and
     * sync-policy selection to [capturePolicy] before touching the contents.
     */
    private suspend fun CoroutineScope.pollPasteboard(firstRead: Boolean) {
        val currentChangeCount = MacAppUtils.getPasteboardChangeCount()
        if (changeCount == currentChangeCount) return

        logger.info { "currentChangeCount $currentChangeCount changeCount $changeCount" }
        val config = configManager.getCurrentConfig()
        val firstChange = firstRead && changeCount == config.lastPasteboardChangeCount
        changeCount = currentChangeCount

        if (firstChange && config.enableSkipPreLaunchPasteboardContent) {
            logger.debug { "Ignoring prior pasteboard" }
            return
        }

        val types = MacAppUtils.getPasteboardTypes(currentChangeCount)
        if (types == null) {
            logger.debug { "Pasteboard moved past change count $currentChangeCount while reading types" }
            return
        }

        consumeChange(
            expectedChangeCount = currentChangeCount,
            firstChange = firstChange,
            types = types,
        )
    }

    private suspend fun CoroutineScope.consumeChange(
        expectedChangeCount: Int,
        firstChange: Boolean,
        types: Set<String>,
    ) {
        val decision =
            capturePolicy.evaluate(
                metadata = ClipboardMetadata(types = types),
                resolveExplicitSource = {
                    if (ClipboardConflictRules.MACOS_PASTEBOARD_SOURCE_TYPE in types) {
                        appWindowManager.getPasteboardSourceAppName(expectedChangeCount)
                    } else {
                        null
                    }
                },
                resolveFallbackSource = { resolveFrontmostSource(firstChange) },
            )
        val capture =
            when (decision) {
                is ClipboardCaptureDecision.Ignore -> {
                    logger.debug { "Ignoring pasteboard change: ${decision.reason}" }
                    return
                }
                is ClipboardCaptureDecision.Capture -> decision
            }

        val contents =
            readUnchangedSnapshot(expectedChangeCount, MacAppUtils::getPasteboardChangeCount, logger) {
                controlUtils.exponentialBackoffUntilValid(
                    initTime = 20L,
                    maxTime = 1000L,
                    isValidResult = ::isValidContents,
                ) {
                    getPasteboardContentsBySafe()
                }
            }
        if (contents == null || contents == ownerTransferable) return

        ownerTransferable = contents
        launch(CoroutineName("MacPasteboardServiceConsumer")) {
            pasteConsumer.consume(
                DesktopReadTransferable(contents),
                capture.sourceContext,
            )
        }
    }

    private suspend fun resolveFrontmostSource(firstChange: Boolean): String? {
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

    companion object {
        /**
         * Reads the pasteboard and keeps the result only if no other write landed
         * while it was being read. AWT reads one type at a time, so a write in
         * between yields a mix of both contents, and the source and concealed
         * checks made before the read describe only the first. Discarding leaves
         * the new count for the next poll, which reads it whole with its own source
         * (the same rule as ClipboardEventPipeline on Windows).
         */
        suspend fun <T> readUnchangedSnapshot(
            changeCount: Int,
            readChangeCount: () -> Int,
            logger: KLogger,
            read: suspend () -> T?,
        ): T? {
            val snapshot = read() ?: return null
            val changeCountNow = readChangeCount()
            if (changeCountNow != changeCount) {
                logger.warn {
                    "Discarding pasteboard snapshot at change count $changeCount: " +
                        "it moved to $changeCountNow during the read"
                }
                return null
            }
            return snapshot
        }
    }
}
