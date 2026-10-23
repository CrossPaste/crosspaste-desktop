package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.User32.Companion.INSTANCE
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

class WindowFocusRecorder {
    private val logger = KotlinLogging.logger {}

    private var hookHandle: WinNT.HANDLE? = null

    private lateinit var eventProc: WinUser.WinEventProc

    private val _lastWinAppInfo: MutableStateFlow<WinAppInfo?> = MutableStateFlow(null)

    val lastWinAppInfo: StateFlow<WinAppInfo?> = _lastWinAppInfo

    // Preserve the HWND of each event: a later foreground query can miss an entire
    // gain/loss pair while the EDT is busy. Listeners only enqueue work on the EDT.
    private val foregroundListeners = CopyOnWriteArraySet<(HWND?) -> Unit>()

    fun subscribeForegroundChanges(listener: (HWND?) -> Unit): AutoCloseable {
        foregroundListeners.add(listener)
        return AutoCloseable { foregroundListeners.remove(listener) }
    }

    private val previousAppFilter = WindowsPreviousAppFilter(INSTANCE, Kernel32.INSTANCE.GetCurrentProcessId())

    private val scope = namedScope(ioDispatcher, "WindowFocusRecorder")

    fun start() {
        if (hookHandle != null) return

        scope.launch {
            eventProc =
                object : WinUser.WinEventProc {
                    override fun callback(
                        hWinEventHook: WinNT.HANDLE?,
                        event: WinDef.DWORD?,
                        hwnd: HWND?,
                        idObject: WinDef.LONG?,
                        idChild: WinDef.LONG?,
                        dwEventThread: WinDef.DWORD?,
                        dwmsEventTime: WinDef.DWORD?,
                    ) {
                        foregroundListeners.forEach { it(hwnd) }
                        if (hwnd != null && isValidWindow(hwnd)) {
                            _lastWinAppInfo.value = WinAppInfo.createWinAppInfo(hwnd)
                        }
                    }
                }

            hookHandle =
                INSTANCE.SetWinEventHook(
                    User32.EVENT_SYSTEM_FOREGROUND,
                    User32.EVENT_SYSTEM_FOREGROUND,
                    null,
                    eventProc,
                    0,
                    0,
                    User32.WINEVENT_OUTOFCONTEXT,
                )

            val msg = WinUser.MSG()
            while (INSTANCE.GetMessage(msg, null, 0, 0) != -1) {
                INSTANCE.TranslateMessage(msg)
                INSTANCE.DispatchMessage(msg)
            }
        }
    }

    fun stop() {
        hookHandle?.let {
            INSTANCE.UnhookWinEvent(it)
            hookHandle = null
        }
    }

    private fun isValidWindow(hwnd: HWND): Boolean {
        val isValidWindow = previousAppFilter.accepts(hwnd)
        if (isValidWindow) {
            logger.debug {
                "Active window changed: title=${windowTitle(
                    hwnd,
                )}, className=${previousAppFilter.className(hwnd)}"
            }
        }
        return isValidWindow
    }

    private fun windowTitle(hwnd: HWND): String {
        val titleLength = INSTANCE.GetWindowTextLengthW(hwnd)
        if (titleLength == 0) return ""
        val buffer = CharArray(titleLength + 1)
        INSTANCE.GetWindowTextW(hwnd, buffer, titleLength + 1)
        return Native.toString(buffer).trim()
    }
}
