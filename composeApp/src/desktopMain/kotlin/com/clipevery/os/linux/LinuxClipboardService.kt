package com.clipevery.os.linux

import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.TransferableProducer
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.linux.api.X11Api
import com.clipevery.os.linux.api.XFixes
import com.clipevery.utils.cpuDispatcher
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.unix.X11.XA_PRIMARY
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

class LinuxClipboardService(
    override val appWindowManager: AppWindowManager,
    override val clipDao: ClipDao,
    override val configManager: ConfigManager,
    override val clipConsumer: TransferableConsumer,
    override val clipProducer: TransferableProducer,
) : ClipboardService {
    override val logger: KLogger = KotlinLogging.logger {}

    private var changeCount = configManager.config.lastClipboardChangeCount

    override var owner: Boolean = false

    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override val clipboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

    private val serviceScope = CoroutineScope(cpuDispatcher + SupervisorJob())

    private var job: Job? = null

    init {
        serviceScope.launch {
            for (task in clipboardChannel) {
                task()
            }
        }
    }

    private fun run(): Job {
        return serviceScope.launch(CoroutineName("LinuxClipboardService")) {
            val x11 = X11Api.INSTANCE
            x11.XOpenDisplay(null)?.let { display ->
                try {
                    val XFixesSetSelectionOwnerNotifyMask = (1 shl 0).toLong()

                    val rootWindow = x11.XDefaultRootWindow(display)
                    val clipboardAtom = x11.XInternAtom(display, "CLIPBOARD", false)

                    XFixes.INSTANCE.XFixesSelectSelectionInput(
                        display,
                        rootWindow,
                        XA_PRIMARY,
                        NativeLong(XFixesSetSelectionOwnerNotifyMask),
                    )
                    XFixes.INSTANCE.XFixesSelectSelectionInput(
                        display,
                        rootWindow,
                        clipboardAtom,
                        NativeLong(XFixesSetSelectionOwnerNotifyMask),
                    )

                    val event = X11.XEvent()
                    while (isActive) {
                        x11.XNextEvent(display, event)

                        logger.info { "notify change event" }
                        changeCount++
                        val start = System.currentTimeMillis()
                        val source = appWindowManager.getCurrentActiveAppName()
                        val end = System.currentTimeMillis()

                        val delay = 20 + start - end

                        if (delay > 0) {
                            delay(delay)
                        }

                        val contents = getContents()
                        if (contents != ownerTransferable) {
                            contents?.let {
                                launch(CoroutineName("MacClipboardServiceConsumer")) {
                                    clipConsumer.consume(it, source, remote = false)
                                }
                            }
                        }
                    }
                } finally {
                    x11.XCloseDisplay(display)
                }
            }
        }
    }

    private suspend fun getContents(): Transferable? {
        var contents = systemClipboard.getContents(null)
        var sum = 0L
        var waiting = 20L
        while (!isValidContents(contents) && sum < 1000L) {
            delay(waiting)
            sum += waiting
            waiting *= 2
            contents = systemClipboard.getContents(null)
        }
        return contents
    }

    override fun start() {
        if (configManager.config.enableClipboardListening) {
            if (job?.isActive != true) {
                job = run()
            }
        }
    }

    override fun stop() {
        job?.cancel()
        configManager.updateConfig { it.copy(lastClipboardChangeCount = changeCount) }
    }

    override fun toggle() {
        val enableClipboardListening = configManager.config.enableClipboardListening
        if (enableClipboardListening) {
            stop()
        } else {
            start()
        }
        configManager.updateConfig { it.copy(enableClipboardListening = !enableClipboardListening) }
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }
}
