package com.crosspaste.paste

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.utils.DesktopControlUtils.blockEnsureMinExecutionTime
import com.crosspaste.utils.DesktopControlUtils.blockExponentialBackoffUntilValid
import com.crosspaste.utils.cpuDispatcher
import com.crosspaste.utils.ioDispatcher
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

class WindowsPasteboardService(
    override val appWindowManager: DesktopAppWindowManager,
    override val pasteDao: PasteDao,
    override val configManager: ConfigManager,
    override val currentPaste: CurrentPaste,
    override val pasteConsumer: TransferableConsumer,
    override val pasteProducer: TransferableProducer,
) : AbstractPasteboardService(), User32.WNDPROC {
    override val logger: KLogger = KotlinLogging.logger {}

    @Volatile
    private var existNew = false

    private var changeCount = configManager.config.lastPasteboardChangeCount

    @Volatile
    override var owner = false

    @Volatile
    override var ownerTransferable: Transferable? = null

    override val systemClipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override val pasteboardChannel: Channel<suspend () -> Unit> = Channel(Channel.UNLIMITED)

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
            for (task in pasteboardChannel) {
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
        val currentPlatform = getPlatform()
        if (currentPlatform.is64bit()) {
            User32.INSTANCE.SetWindowLongPtr(
                viewer,
                User32.GWL_WNDPROC,
                this,
            )
        } else {
            User32.INSTANCE.SetWindowLong(
                viewer,
                User32.GWL_WNDPROC,
                this,
            )
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
        if (job?.isActive != true) {
            job =
                serviceScope.launch(CoroutineName("WindowsPasteboardService")) {
                    run()
                }
        }
    }

    override fun stop() {
        Kernel32.INSTANCE.SetEvent(event)
        job?.cancel()
        configManager.updateConfig("lastPasteboardChangeCount", changeCount)
    }

    private fun onChange() {
        try {
            val source =
                blockEnsureMinExecutionTime(delayTime = 20) {
                    appWindowManager.getCurrentActiveAppName()
                }

            val contents =
                blockExponentialBackoffUntilValid(
                    initTime = 20L,
                    maxTime = 1000L,
                    isValidResult = ::isValidContents,
                ) {
                    getPasteboardContentsBySafe()
                }

            contents?.let {
                serviceConsumerScope.launch(CoroutineName("WindowsPasteboardConsumer")) {
                    if (it != ownerTransferable) {
                        ownerTransferable = it
                        val pasteTransferable = DesktopReadTransferable(it)
                        // in windows, we don't know if the pasteboard is local or remote
                        pasteConsumer.consume(pasteTransferable, source, remote = false)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to consume transferable" }
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
                } else if (nextViewer != null) {
                    // Otherwise, pass the message to the next link
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

            User32.WM_DESTROY ->
                User32.INSTANCE.ChangeClipboardChain(
                    viewer,
                    nextViewer,
                )
        }
        return User32.INSTANCE.DefWindowProc(hWnd, uMsg, uParam, lParam).toInt()
    }
}
