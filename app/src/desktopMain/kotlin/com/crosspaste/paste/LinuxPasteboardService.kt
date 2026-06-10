package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.notification.NotificationManager
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import java.nio.charset.Charset

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
    }

    override val logger: KLogger = KotlinLogging.logger {}

    private val controlUtils = getControlUtils()

    private var changeCount = configManager.getCurrentConfig().lastPasteboardChangeCount

    override var owner: Boolean = false

    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    private var job: Job? = null

    init {
        startRemotePasteboardListener()
    }

    private fun run(): Job =
        serviceScope.launch(CoroutineName("LinuxPasteboardService")) {
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
     * (the backoff in [onChange] can add up to ~1s, plus the dispatch of the
     * consumer coroutine this runs in), so a copy performed inside
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
