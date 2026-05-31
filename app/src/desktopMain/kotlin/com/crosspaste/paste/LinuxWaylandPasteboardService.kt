package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteText
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

    /**
     * Pending echoes from our own writes. Each [writePasteboard] enqueues an
     * entry; each [onSelection] tries to match-and-consume by text content
     * (preferred) or fall through by FIFO if the payload has no text mime.
     * Entries older than [SELF_WRITE_WINDOW_MS] expire so a missed echo can't
     * pin the queue and eat future external changes.
     */
    private val pendingSelfWrites: ArrayDeque<SelfWrite> = ArrayDeque()
    private val pendingSelfWritesLock = Any()

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
        recordSelfWrite((pasteItem as? PasteText)?.text)
        super.writePasteboard(pasteItem, transferable)
    }

    override fun writePasteboard(
        pasteData: PasteData,
        transferable: DesktopWriteTransferable,
    ) {
        recordSelfWrite(pasteData.getPasteItem(PasteText::class)?.text)
        super.writePasteboard(pasteData, transferable)
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }

    private fun recordSelfWrite(text: String?) {
        synchronized(pendingSelfWritesLock) {
            pendingSelfWrites.addLast(SelfWrite(System.nanoTime(), text))
        }
    }

    /**
     * Returns true if [payload] should be treated as the echo of one of our
     * own writes. Matches by text content when possible (handles concurrent
     * writes ordered differently than they were enqueued); otherwise consumes
     * the oldest non-text entry within the time window.
     */
    private fun consumeSelfEchoIfMatches(payload: Map<String, ByteArray>): Boolean {
        val now = System.nanoTime()
        synchronized(pendingSelfWritesLock) {
            // Drop expired entries first so a single missed echo can't pin
            // the queue forever.
            while (pendingSelfWrites.isNotEmpty() &&
                ageMillis(now, pendingSelfWrites.first().timestamp) > SELF_WRITE_WINDOW_MS
            ) {
                pendingSelfWrites.removeFirst()
            }
            if (pendingSelfWrites.isEmpty()) return false

            val incomingText = extractText(payload)
            if (incomingText != null) {
                val iter = pendingSelfWrites.iterator()
                while (iter.hasNext()) {
                    val sw = iter.next()
                    if (sw.text != null && sw.text == incomingText) {
                        iter.remove()
                        return true
                    }
                }
                // Text payload but no enqueued text-match — it's a genuine
                // external change (likely an app pasted something while we
                // had a non-text write pending).
                return false
            }

            // Non-text incoming: consume the oldest non-text pending write.
            val iter = pendingSelfWrites.iterator()
            while (iter.hasNext()) {
                val sw = iter.next()
                if (sw.text == null) {
                    iter.remove()
                    return true
                }
            }
            return false
        }
    }

    private fun extractText(payload: Map<String, ByteArray>): String? {
        for (mime in TEXT_MIME_PRIORITY) {
            payload[mime]?.let { return it.toString(Charsets.UTF_8) }
        }
        return null
    }

    private fun ageMillis(
        now: Long,
        nanos: Long,
    ): Long = (now - nanos) / 1_000_000L

    private fun onSelection(payload: Map<String, ByteArray>) {
        if (payload.isEmpty()) return

        if (skipFirstSelection.compareAndSet(true, false)) {
            logger.info { "Skipping pre-launch Wayland selection (${payload.size} mime(s))" }
            return
        }
        if (consumeSelfEchoIfMatches(payload)) {
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

    private data class SelfWrite(
        val timestamp: Long,
        val text: String?,
    )

    companion object {
        /**
         * How long a self-write stays "expected" before being treated as a
         * dropped echo. Wayland round-trip is typically < 50ms; 1500ms gives
         * generous headroom while still expiring within a single user-action
         * gap so a stuck entry can't eat the next real external copy.
         */
        private const val SELF_WRITE_WINDOW_MS = 1500L

        // Order matters: more specific encodings first so we don't pick up a
        // less-precise text payload when both are present.
        private val TEXT_MIME_PRIORITY =
            listOf(
                "text/plain;charset=utf-8",
                "text/plain",
                "UTF8_STRING",
                "STRING",
                "TEXT",
            )
    }
}
