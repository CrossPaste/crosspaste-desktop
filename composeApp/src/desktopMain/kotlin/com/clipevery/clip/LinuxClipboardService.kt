package com.clipevery.clip

import com.clipevery.app.AppWindowManager
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.linux.api.X11Api
import com.clipevery.os.linux.api.XFixes
import com.clipevery.utils.ControlUtils.ensureMinExecutionTime
import com.clipevery.utils.ControlUtils.exponentialBackoffUntilValid
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

    companion object {
        const val XFIXES_SET_SELECTION_OWNER_NOTIFY_MASK = (1 shl 0).toLong()
    }

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
                    val rootWindow = x11.XDefaultRootWindow(display)
                    val clipboardAtom = x11.XInternAtom(display, "CLIPBOARD", false)

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
                                    getClipboardContentsBySafe()
                                }
                            if (contents != ownerTransferable) {
                                contents?.let {
                                    launch(CoroutineName("LinuxClipboardServiceConsumer")) {
                                        clipConsumer.consume(it, source, remote = false)
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
