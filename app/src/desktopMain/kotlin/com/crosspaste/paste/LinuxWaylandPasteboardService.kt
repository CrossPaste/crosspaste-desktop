package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.platform.linux.wayland.WaylandClipboardSession
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.getControlUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native-Wayland implementation of the paste service.
 *
 * Reads via `zwlr_data_control_unstable_v1` ([WaylandClipboardSession]),
 * bypassing the XWayland XClipboard bridge that causes the multi-second
 * `Owner failed to convert data` retries described in issue #4490.
 *
 * Writes still flow through AWT's [systemClipboard] — write performance is not
 * the bottleneck the issue reports, and AWT writes happen while CrossPaste is
 * focused (after a user UI action), so the focus-required `wl_data_device`
 * path is fine.
 */
class LinuxWaylandPasteboardService(
    override val appWindowManager: DesktopAppWindowManager,
    override val configManager: CommonConfigManager,
    override val currentPaste: CurrentPaste,
    override val notificationManager: NotificationManager,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
    override val pasteReleaseService: PasteReleaseService,
    override val soundService: SoundService,
    override val sourceExclusionService: DesktopSourceExclusionService,
) : AbstractPasteboardService() {

    override val logger: KLogger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    override var owner: Boolean = false

    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private val skipFirstSelection: AtomicBoolean =
        AtomicBoolean(configManager.getCurrentConfig().enableSkipPreLaunchPasteboardContent)

    private val expectingOwnEcho: AtomicBoolean = AtomicBoolean(false)

    // Each toggle off/on rebuilds the session — WaylandClipboardSession is
    // single-use (its dispatch thread tears down all native resources on
    // close).
    @Volatile
    private var session: WaylandClipboardSession? = null

    init {
        startRemotePasteboardListener()
    }

    @Synchronized
    override fun start() {
        if (session != null) return
        val newSession =
            runCatching { WaylandClipboardSession.connect() }
                .onFailure { e -> logger.warn(e) { "Wayland clipboard reconnect failed" } }
                .getOrNull()
        if (newSession == null) {
            logger.warn { "Wayland clipboard unavailable on start — clipboard monitoring disabled" }
            return
        }
        newSession.onSelection { payload -> onSelection(payload) }
        newSession.start()
        session = newSession
    }

    @Synchronized
    override fun stop() {
        val s = session ?: return
        session = null
        s.close()
    }

    override fun writePasteboard(
        pasteItem: PasteItem,
        transferable: DesktopWriteTransferable,
    ) {
        expectingOwnEcho.set(true)
        super.writePasteboard(pasteItem, transferable)
    }

    override fun writePasteboard(
        pasteData: PasteData,
        transferable: DesktopWriteTransferable,
    ) {
        expectingOwnEcho.set(true)
        super.writePasteboard(pasteData, transferable)
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }

    private fun onSelection(payload: Map<String, ByteArray>) {
        if (payload.isEmpty()) return

        if (skipFirstSelection.compareAndSet(true, false)) {
            logger.info { "Skipping pre-launch Wayland selection (${payload.size} mime(s))" }
            return
        }
        if (expectingOwnEcho.compareAndSet(true, false)) {
            logger.debug { "Skipping own clipboard echo (${payload.size} mime(s))" }
            return
        }

        logger.info { "notify change event (wayland) mimes=${payload.keys}" }

        serviceScope.launch(CoroutineName("LinuxWaylandPasteboardServiceConsumer")) {
            val source =
                controlUtils
                    .ensureMinExecutionTime(delayTime = 20) {
                        appWindowManager.getCurrentActiveAppName()
                    }.getOrNull()

            if (sourceExclusionService.isExcluded(source)) {
                logger.debug { "Ignoring excluded source: $source" }
                return@launch
            }

            val transferable = WaylandSyntheticTransferable(payload)
            pasteConsumer.consume(
                DesktopReadTransferable(transferable),
                PasteSourceContext(source = source, remote = false),
            )
        }
    }
}
