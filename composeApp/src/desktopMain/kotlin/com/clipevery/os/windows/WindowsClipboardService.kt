package com.clipevery.os.windows

import com.clipevery.clip.ClipboardService
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.TransferableProducer
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.os.windows.api.User32
import com.clipevery.platform.currentPlatform
import com.clipevery.utils.cpuDispatcher
import com.clipevery.utils.ioDispatcher
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.MSG
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import kotlin.math.min

class WindowsClipboardService(
    override val clipDao: ClipDao,
    override val configManager: ConfigManager,
    override val clipConsumer: TransferableConsumer,
    override val clipProducer: TransferableProducer,
) : ClipboardService, User32.WNDPROC {
    override val logger: KLogger = KotlinLogging.logger {}

    @Volatile
    private var existNew = false

    private var changeCount = configManager.config.lastClipboardChangeCount

    @Volatile
    override var owner = false

    @Volatile
    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override val clipboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

    private val serviceScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val serviceConsumerScope = CoroutineScope(cpuDispatcher + SupervisorJob())

    private var job: Job? = null
    private var viewer: HWND? = null
    private var nextViewer: HWND? = null
    private val event =
        Kernel32.INSTANCE.CreateEvent(
            null,
            false,
            false,
            null,
        )

    init {
        serviceConsumerScope.launch {
            for (task in clipboardChannel) {
                task()
            }
        }
    }

    private fun run() {
        viewer =
            User32.INSTANCE.CreateWindowEx(
                0, "STATIC", "", 0, 0, 0, 0, 0,
                null, 0, 0, null,
            )
        nextViewer = User32.INSTANCE.SetClipboardViewer(viewer)
        val currentPlatform = currentPlatform()
        if (currentPlatform.is64bit()) {
            User32.INSTANCE.SetWindowLongPtr(viewer, User32.GWL_WNDPROC, this)
        } else {
            User32.INSTANCE.SetWindowLong(viewer, User32.GWL_WNDPROC, this)
        }
        val msg = MSG()
        val handles = arrayOf(event)
        while (true) {
            val result: Int =
                User32.INSTANCE.MsgWaitForMultipleObjects(
                    handles.size,
                    handles,
                    false,
                    Kernel32.INFINITE,
                    User32.QS_ALLINPUT,
                )

            existNew = true

            if (result == Kernel32.WAIT_OBJECT_0) {
                User32.INSTANCE.DestroyWindow(viewer)
                return
            }
            if (result != Kernel32.WAIT_OBJECT_0 + handles.size) {
                // Serious problem!
                break
            }
            while (User32.INSTANCE.PeekMessage(
                    msg,
                    null,
                    0,
                    0,
                    User32.PM_REMOVE,
                )
            ) {
                User32.INSTANCE.TranslateMessage(msg)
                User32.INSTANCE.DispatchMessage(msg)
            }
        }
    }

    override fun lostOwnership(
        clipboard: Clipboard?,
        contents: Transferable?,
    ) {
        owner = false
    }

    override fun start() {
        if (configManager.config.enableClipboardListening) {
            if (job?.isActive != true) {
                job =
                    serviceScope.launch(CoroutineName("WindowsClipboardService")) {
                        run()
                    }
            }
        }
    }

    override fun stop() {
        Kernel32.INSTANCE.SetEvent(event)
        job?.cancel()
        configManager.updateConfig { it.copy(lastClipboardChangeCount = changeCount) }
    }

    @Synchronized
    override fun toggle() {
        val enableClipboardListening = configManager.config.enableClipboardListening
        if (enableClipboardListening) {
            stop()
        } else {
            start()
        }
        configManager.updateConfig { it.copy(enableClipboardListening = !enableClipboardListening) }
    }

    private fun onChange() {
        var contents: Transferable? = null
        var waitTime = 20L
        var totalWaitTime = 0L
        do {
            Thread.sleep(waitTime)
            totalWaitTime += waitTime
            waitTime = min(waitTime * 2, 1000)
            try {
                contents = systemClipboard.getContents(null)
            } catch (e: IllegalStateException) {
                logger.warn(e) { "systemClipboard get contents fail" }
            }

            if (contents == null && totalWaitTime + waitTime > 1000) {
                logger.error { "systemClipboard get contents timeout" }
                break
            }
        } while (contents == null)

        contents?.let {
            serviceConsumerScope.launch(CoroutineName("WindowsClipboardConsumer")) {
                if (it != ownerTransferable) {
                    // in windows, we don't know if the clipboard is local or remote
                    clipConsumer.consume(it, remote = false)
                }
            }
        }
    }

    override fun callback(
        hWnd: HWND?,
        uMsg: Int,
        uParam: WPARAM?,
        lParam: LPARAM?,
    ): Int {
        when (uMsg) {
            User32.WM_CHANGECBCHAIN -> {
                // If the next window is closing, repair the chain.
                if (nextViewer!!.toNative() == uParam!!.toNative()) {
                    nextViewer =
                        HWND(
                            Pointer.createConstant(lParam!!.toLong()),
                        )
                } // Otherwise, pass the message to the next link.
                else if (nextViewer != null) {
                    User32.INSTANCE.SendMessage(nextViewer, uMsg, uParam, lParam)
                }
                return 0
            }

            User32.WM_DRAWCLIPBOARD -> {
                if (existNew) {
                    existNew = false
                    try {
                        val clipboardSequenceNumber = User32.INSTANCE.GetClipboardSequenceNumber()
                        if (changeCount != clipboardSequenceNumber) {
                            changeCount = clipboardSequenceNumber
                            onChange()
                        }
                    } finally {
                        User32.INSTANCE.SendMessage(nextViewer, uMsg, uParam, lParam)
                    }
                }
                return 0
            }

            User32.WM_DESTROY -> User32.INSTANCE.ChangeClipboardChain(viewer, nextViewer)
        }
        return User32.INSTANCE.DefWindowProc(hWnd, uMsg, uParam, lParam)
    }
}
