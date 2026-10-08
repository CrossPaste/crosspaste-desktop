package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.platform.linux.LinuxActiveAppResolver
import com.crosspaste.platform.linux.api.WaylandClipboardMonitor
import com.crosspaste.platform.linux.api.WaylandSelection
import com.crosspaste.platform.linux.api.X11Api
import com.crosspaste.platform.linux.api.X11ClipboardReader
import com.crosspaste.platform.linux.api.XFixes
import com.crosspaste.platform.linux.api.XFixesSelectionNotifyEvent
import com.crosspaste.sound.SoundService
import com.crosspaste.utils.getControlUtils
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.XA_PRIMARY
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import java.nio.charset.Charset

/**
 * Linux clipboard monitoring, with two backends:
 *
 *  - **Wayland sessions** read the compositor's own clipboard through the
 *    data-control protocol ([WaylandClipboardMonitor]). The X11 `CLIPBOARD`
 *    selection that AWT sees under XWayland is only a mirror the compositor
 *    keeps for X clients, and it is incomplete — KWin never mirrors images
 *    copied in native Wayland apps, so the X11 path below never even notices
 *    the copy (#5167). Copies made by XWayland apps are bridged *into* the
 *    Wayland clipboard by every compositor, so this one channel sees them all.
 *  - **X11 sessions**, and Wayland compositors without data-control (GNOME),
 *    watch the X11 selection with XFixes and read it through AWT.
 *
 * Writes go through AWT in both modes; in Wayland mode they carry
 * [PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR] so the bridged echo of our own
 * write is recognised and skipped.
 */
class LinuxPasteboardService(
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

    companion object {
        const val XFIXES_SET_SELECTION_OWNER_NOTIFY_MASK = (1 shl 0).toLong()

        // Generous headroom for XWayland's bridge to start serving the new
        // selection; native X11 owners typically answer the first probe.
        private const val CLIPBOARD_READY_TIMEOUT_MS = 2000L
    }

    override val logger: KLogger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private var changeCount = configManager.getCurrentConfig().lastPasteboardChangeCount

    override var owner: Boolean = false

    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private var job: Job? = null

    private val isWaylandSession = LinuxActiveAppResolver.isWaylandSession()

    @Volatile
    private var waylandMonitor: WaylandClipboardMonitor? = null

    init {
        startRemotePasteboardListener()
    }

    private fun run(): Job =
        serviceScope.launch(CoroutineName("LinuxPasteboardService")) {
            if (isWaylandSession && runWayland()) {
                return@launch
            }
            runX11()
        }

    // ---- Wayland (data-control) ----

    /**
     * Starts the native Wayland monitor and consumes its selections until
     * cancelled. Returns false without consuming anything when data-control is
     * unavailable, so the caller falls back to X11.
     */
    private suspend fun CoroutineScope.runWayland(): Boolean {
        // Conflated: a copy that lands while an earlier one is still being read
        // supersedes it; the read notices (isCurrent) and the newer one is taken.
        val selections = Channel<WaylandSelection>(Channel.CONFLATED)
        val monitor = WaylandClipboardMonitor { selection -> selections.trySend(selection) }
        if (!monitor.start()) {
            selections.close()
            return false
        }
        waylandMonitor = monitor
        try {
            for (selection in selections) {
                try {
                    onWaylandSelection(this, monitor, selection)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Failed to consume Wayland selection" }
                }
            }
        } finally {
            waylandMonitor = null
            monitor.stop()
        }
        return true
    }

    private suspend fun onWaylandSelection(
        scope: CoroutineScope,
        monitor: WaylandClipboardMonitor,
        selection: WaylandSelection,
    ) {
        val mimeTypes = selection.mimeTypes
        if (selection.initial) {
            if (configManager.getCurrentConfig().enableSkipPreLaunchPasteboardContent) {
                logger.debug { "Ignoring pre-launch Wayland clipboard content" }
                return
            }
        } else {
            logger.info { "notify change event (wayland)" }
            changeCount++
        }
        if (mimeTypes.contains(PasteDataFlavors.CROSSPASTE_ORIGIN_MIME)) {
            logger.debug { "Ignoring the bridged echo of our own clipboard write" }
            return
        }
        if (PasswordManagerHints.isConcealedOnLinux(mimeTypes)) {
            logger.debug { "Ignoring concealed clipboard content" }
            return
        }

        val source =
            if (selection.initial) {
                null
            } else {
                controlUtils
                    .ensureMinExecutionTime(delayTime = 20) {
                        appWindowManager.getCurrentActiveAppName()
                    }.getOrNull()
            }
        if (sourceExclusionService.isExcluded(source)) {
            logger.debug { "Ignoring excluded source: $source" }
            return
        }

        val contents =
            WaylandClipboardSnapshot.create(mimeTypes) { mimeType -> monitor.read(selection, mimeType) }
        if (contents == null) {
            logger.debug { "Wayland selection had nothing readable: $mimeTypes" }
            return
        }
        // A copy that landed mid-read may have left the snapshot a mix of two
        // clipboards; that newer copy is queued and gets read on its own.
        if (!monitor.isCurrent(selection)) {
            logger.debug { "Wayland selection replaced while reading, dropping the partial snapshot" }
            return
        }
        ownerTransferable = contents
        scope.launch(CoroutineName("LinuxPasteboardServiceConsumer")) {
            pasteConsumer.consume(
                DesktopReadTransferable(contents),
                PasteSourceContext(source = source, remote = false),
            )
        }
    }

    // ---- X11 (XFixes + AWT) ----

    private suspend fun CoroutineScope.runX11() {
        val firstChange = changeCount == configManager.getCurrentConfig().lastPasteboardChangeCount

        if (firstChange && !configManager.getCurrentConfig().enableSkipPreLaunchPasteboardContent) {
            onChange(this, true)
        }

        val x11 = X11Api.INSTANCE
        x11.XOpenDisplay(null)?.let { display ->
            runCatching {
                val rootWindow = x11.XDefaultRootWindow(display)
                val clipboardAtom = x11.XInternAtom(display, "CLIPBOARD", false)

                val eventBaseReturnBuffer = IntByReference()
                val errorBaseReturnBuffer = IntByReference()

                if (XFixes.INSTANCE.XFixesQueryExtension(display, eventBaseReturnBuffer, errorBaseReturnBuffer) ==
                    0
                ) {
                    throw RuntimeException("XFixes extension missing")
                }

                val eventBaseReturn = eventBaseReturnBuffer.value

                XFixes.INSTANCE.XFixesSelectSelectionInput(
                    display,
                    rootWindow,
                    XA_PRIMARY,
                    NativeLong(XFIXES_SET_SELECTION_OWNER_NOTIFY_MASK),
                )
                XFixes.INSTANCE.XFixesSelectSelectionInput(
                    display,
                    rootWindow,
                    clipboardAtom,
                    NativeLong(XFIXES_SET_SELECTION_OWNER_NOTIFY_MASK),
                )

                val event = X11.XEvent()
                while (isActive) {
                    runCatching {
                        x11.XNextEvent(display, event)

                        if (event.type == (eventBaseReturn + XFixes.XFixesSelectionNotify)) {
                            val selectionNotify = XFixesSelectionNotifyEvent(event.pointer)

                            // Ignore selected events and keep copy events
                            if (selectionNotify.selection?.toLong() == clipboardAtom.toLong()) {
                                logger.info { "notify change event" }
                                changeCount++
                                onChange(this)
                            }
                            selectionNotify.clear()
                        }
                    }.onFailure { e ->
                        logger.error(e) { "Failed to consume transferable" }
                    }
                }
            }.apply {
                x11.XCloseDisplay(display)
            }
        }
    }

    private suspend fun onChange(
        scope: CoroutineScope,
        firstChange: Boolean = false,
    ) {
        val source =
            if (firstChange) {
                null
            } else {
                controlUtils
                    .ensureMinExecutionTime(delayTime = 20) {
                        appWindowManager.getCurrentActiveAppName()
                    }.getOrNull()
            }

        if (sourceExclusionService.isExcluded(source)) {
            logger.debug { "Ignoring excluded source: $source" }
            return
        }

        // XFixes only says the selection owner changed, not that the owner can
        // already serve data. Under XWayland the bridge keeps failing conversions
        // ("Owner failed to convert data") for hundreds of ms after the notify,
        // so probe TARGETS directly and start the AWT read only once the owner
        // actually answers — the read below then almost always succeeds first try.
        // The same TARGETS answer carries the password-manager hint, if any.
        val targets = X11ClipboardReader.awaitClipboardTargets(CLIPBOARD_READY_TIMEOUT_MS)
        if (targets == null) {
            logger.warn {
                "Clipboard owner not ready within ${CLIPBOARD_READY_TIMEOUT_MS}ms, reading anyway"
            }
        } else if (PasswordManagerHints.isConcealedOnLinux(targets)) {
            logger.debug { "Ignoring concealed clipboard content" }
            return
        }

        val contents =
            controlUtils.linearBackoffUntilValid(
                initTime = 20L,
                maxTime = 1000L,
                isValidResult = ::isValidContents,
            ) {
                getPasteboardContentsBySafe()
            }
        if (!isValidContents(contents)) {
            logger.warn { "Clipboard contents still invalid after backoff; this change may be lost" }
        }
        if (contents != ownerTransferable) {
            contents?.let {
                ownerTransferable = it
                scope.launch(CoroutineName("LinuxPasteboardServiceConsumer")) {
                    val pasteTransferable = DesktopReadTransferable(correctHtmlEncoding(it))
                    pasteConsumer.consume(
                        pasteTransferable,
                        PasteSourceContext(source = source, remote = false),
                    )
                }
            }
        }
    }

    /**
     * AWT exposes `text/html` only through a `charset=Unicode` (UTF-16) flavor,
     * so html published in any other encoding (e.g. UTF-8 from IntelliJ / JBR)
     * arrives mojibaked. Re-read the raw `text/html` bytes straight from the
     * X11 selection and decode them with proper charset detection, overriding
     * just that flavor. Falls back to the original transferable on any failure.
     *
     * Known tradeoff: this raw read happens after AWT captured [transferable]
     * (the readiness probe plus backoff in [onChange] can add up to a few
     * seconds, plus the dispatch of the consumer coroutine this runs in), so a
     * copy performed inside
     * that window would pair the newer clipboard's html with the older entry's
     * other flavors. The window is narrow and the result is still well-formed
     * html, so we accept it — there is no reliable cheap equivalence check
     * between html and the other flavors, and a false mismatch would silently
     * reintroduce the mojibake this fix exists to remove.
     */
    private fun correctHtmlEncoding(transferable: Transferable): Transferable =
        runCatching {
            if (!LinuxHtmlCorrectingTransferable.supportsHtml(transferable)) {
                logger.debug { "correctHtmlEncoding: transferable has no text/html flavor, skipping" }
                return transferable
            }
            val htmlBytes = X11ClipboardReader.readClipboardHtml()
            if (htmlBytes == null) {
                logger.warn {
                    "correctHtmlEncoding: X11 read returned null, falling back to AWT value (may be mojibaked)"
                }
                return transferable
            }
            val knownCharset =
                htmlBytes.charsetName?.let { name ->
                    runCatching { Charset.forName(name) }.getOrNull()
                }
            val html = HtmlClipboardDecoder.decode(htmlBytes.bytes, knownCharset)
            logger.debug { "correctHtmlEncoding: corrected html prefix=${html.take(120)}" }
            LinuxHtmlCorrectingTransferable(transferable, html)
        }.getOrElse { e ->
            logger.warn(e) { "Failed to correct html clipboard encoding" }
            transferable
        }

    override fun writePasteboard(
        pasteItem: PasteItem,
        transferable: DesktopWriteTransferable,
    ) {
        systemClipboard.setContents(markOrigin(transferable), this)
    }

    override fun writePasteboard(
        pasteData: PasteData,
        transferable: DesktopWriteTransferable,
    ) {
        systemClipboard.setContents(markOrigin(transferable), this)
    }

    // Only the Wayland monitor looks for the marker; the X11 path recognises its
    // own write by identity, which the wrapper would break.
    private fun markOrigin(transferable: DesktopWriteTransferable): Transferable =
        if (waylandMonitor != null) LinuxOriginMarkedTransferable(transferable) else transferable

    override fun start() {
        if (job?.isActive != true) {
            job = run()
        }
    }

    override fun stop() {
        job?.cancel()
        configManager.updateConfig("lastPasteboardChangeCount", changeCount)
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }
}
