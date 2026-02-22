package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.linux.api.X11Api
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

class LinuxPasteboardService(
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
                    val pasteTransferable = DesktopReadTransferable(it)
                    pasteConsumer.consume(pasteTransferable, source, remote = false)
                }
            }
        }
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
