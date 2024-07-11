package com.crosspaste.paste

import com.crosspaste.app.AppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.os.linux.api.X11Api
import com.crosspaste.os.linux.api.XFixes
import com.crosspaste.os.linux.api.XFixesSelectionNotifyEvent
import com.crosspaste.utils.DesktopControlUtils.ensureMinExecutionTime
import com.crosspaste.utils.DesktopControlUtils.exponentialBackoffUntilValid
import com.crosspaste.utils.cpuDispatcher
import com.sun.jna.NativeLong
import com.sun.jna.Structure
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.XA_PRIMARY
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable

class LinuxPasteboardService(
    override val appWindowManager: AppWindowManager,
    override val pasteDao: PasteDao,
    override val configManager: ConfigManager,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
) : AbstractPasteboardService() {

    companion object {
        const val XFIXES_SET_SELECTION_OWNER_NOTIFY_MASK = (1 shl 0).toLong()
    }

    override val logger: KLogger = KotlinLogging.logger {}

    private var changeCount = configManager.config.lastPasteboardChangeCount

    override var owner: Boolean = false

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
        return serviceScope.launch(CoroutineName("LinuxPasteboardService")) {
            val x11 = X11Api.INSTANCE
            x11.XOpenDisplay(null)?.let { display ->
                try {
                    val rootWindow = x11.XDefaultRootWindow(display)
                    val clipboardAtom = x11.XInternAtom(display, "CLIPBOARD", false)

                    val eventBaseReturnBuffer = IntByReference()
                    val errorBaseReturnBuffer = IntByReference()

                    if (XFixes.INSTANCE.XFixesQueryExtension(display, eventBaseReturnBuffer, errorBaseReturnBuffer) == 0) {
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
                        try {
                            x11.XNextEvent(display, event)

                            if (event.type == (eventBaseReturn + XFixes.XFixesSelectionNotify)) {
                                val selectionNotify: XFixesSelectionNotifyEvent =
                                    Structure.newInstance(
                                        XFixesSelectionNotifyEvent::class.java,
                                        event.getPointer(),
                                    )
                                selectionNotify.read()

                                // Ignore selected events and keep copy events
                                if (selectionNotify.selection?.toLong() == 0x145L) {
                                    logger.info { "notify change event" }
                                    changeCount++

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
                                            getPasteboardContentsBySafe()
                                        }
                                    if (contents != ownerTransferable) {
                                        contents?.let {
                                            ownerTransferable = it
                                            launch(CoroutineName("LinuxPasteboardServiceConsumer")) {
                                                val pasteTransferable = DesktopReadTransferable(it)
                                                pasteConsumer.consume(pasteTransferable, source, remote = false)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to consume transferable" }
                        }
                    }
                } finally {
                    x11.XCloseDisplay(display)
                }
            }
        }
    }

    override fun start() {
        if (configManager.config.enablePasteboardListening) {
            if (job?.isActive != true) {
                job = run()
            }
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
