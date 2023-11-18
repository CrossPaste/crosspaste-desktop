package com.clipevery.windows

import androidx.compose.runtime.MutableState
import com.clipevery.clip.AbstractClipboard
import com.clipevery.clip.ClipboardEvent
import com.clipevery.windows.api.User32
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.MSG
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class WindowsClipboard
    (private val copyText: MutableState<String>) : AbstractClipboard, User32.WNDPROC {

    private var systemClipboard: Clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()

    private var executor: ExecutorService? = null
    private var viewer: HWND? = null
    private var nextViewer: HWND? = null
    private val event = Kernel32.INSTANCE.CreateEvent(
        null, false,
        false, null
    )

    override fun run() {
        viewer = User32.INSTANCE.CreateWindowEx(
            0, "STATIC", "", 0, 0, 0, 0, 0,
            null, 0, 0, null
        )
        nextViewer = User32.INSTANCE.SetClipboardViewer(viewer)
        User32.INSTANCE.SetWindowLongPtr(viewer, User32.GWL_WNDPROC, this)
        val msg = MSG()
        val handles = arrayOf(event)
        while (true) {
            val result: Int = User32.INSTANCE.MsgWaitForMultipleObjects(
                handles.size, handles, false, Kernel32.INFINITE,
                User32.QS_ALLINPUT
            )
            if (result == Kernel32.WAIT_OBJECT_0) {
                User32.INSTANCE.DestroyWindow(viewer)
                return
            }
            if (result != Kernel32.WAIT_OBJECT_0 + handles.size) {
                // Serious problem!
                break
            }
            while (User32.INSTANCE.PeekMessage(
                    msg, null, 0, 0,
                    User32.PM_REMOVE)
            ) {
                User32.INSTANCE.TranslateMessage(msg)
                User32.INSTANCE.DispatchMessage(msg)
            }
        }
    }

    override fun start() {
        if (executor?.isShutdown != false) {
            executor = Executors.newSingleThreadExecutor { r -> Thread(r, "Clipboard Monitor") }
        }
        executor?.execute(this)
    }

    override fun stop() {
        executor?.let {
            Kernel32.INSTANCE.SetEvent(event)
            it.shutdown()

            try {
                if (!it.awaitTermination(60, TimeUnit.SECONDS)) {
                    it.shutdownNow()

                    if (!it.awaitTermination(60, TimeUnit.SECONDS)) {
                        println("task did not terminate")
                    }
                }
                println("stop ")
            } catch (ie: InterruptedException) {
                // 异常处理
                Thread.currentThread().interrupt()
                // 强制关闭
                it.shutdownNow()
            }
        }
    }

    override fun onChange(event: ClipboardEvent?) {
        val contents: Transferable? = systemClipboard.getContents(null)
        contents?.let {
            if (it.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    copyText.value = contents.getTransferData(DataFlavor.stringFlavor).toString()
                    println(contents.getTransferData(DataFlavor.stringFlavor))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun callback(hWnd: HWND?, uMsg: Int, uParam: WPARAM?, lParam: LPARAM?): Int {
        when (uMsg) {
            User32.WM_CHANGECBCHAIN -> {
                // If the next window is closing, repair the chain.
                if (nextViewer!!.toNative() == uParam!!.toNative()) {
                    nextViewer = HWND(
                        Pointer.createConstant(lParam!!.toLong())
                    )
                } // Otherwise, pass the message to the next link.
                else if (nextViewer != null) {
                    User32.INSTANCE.SendMessage(nextViewer, uMsg, uParam, lParam)
                }
                return 0
            }

            User32.WM_DRAWCLIPBOARD -> {
                try {
                    onChange(ClipboardEvent(this))
                } finally {
                    User32.INSTANCE.SendMessage(nextViewer, uMsg, uParam, lParam)
                }
                return 0
            }

            User32.WM_DESTROY -> User32.INSTANCE.ChangeClipboardChain(viewer, nextViewer)
        }
        return User32.INSTANCE.DefWindowProc(hWnd, uMsg, uParam, lParam)
    }
}