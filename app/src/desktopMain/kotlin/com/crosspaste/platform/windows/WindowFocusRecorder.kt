package com.crosspaste.platform.windows

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.User32.Companion.INSTANCE
import com.crosspaste.utils.ioDispatcher
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WindowFocusRecorder(
    private val appWindowManager: DesktopAppWindowManager,
) {
    private val logger = KotlinLogging.logger {}

    private var hookHandle: WinNT.HANDLE? = null

    private lateinit var eventProc: WinUser.WinEventProc

    private val _lastWinAppInfo: MutableStateFlow<WinAppInfo?> = MutableStateFlow(null)

    val lastWinAppInfo: StateFlow<WinAppInfo?> = _lastWinAppInfo

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

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
        if (!INSTANCE.IsWindowVisible(hwnd)) return false

        val titleLength = INSTANCE.GetWindowTextLengthW(hwnd)

        var title: String? = null

        if (titleLength != 0) {
            val buffer = CharArray(titleLength + 1)
            INSTANCE.GetWindowTextW(hwnd, buffer, titleLength + 1)
            title = Native.toString(buffer).trim()

            if (title == appWindowManager.mainWindowTitle ||
                title == appWindowManager.searchWindowTitle
            ) {
                return false
            }
        }

        val buffer = CharArray(512)
        INSTANCE.GetClassNameW(hwnd, buffer, 512)
        val className = Native.toString(buffer).trim()

        val ignoredClasses =
            setOf(
                "Shell_TrayWnd",
                "Shell_SecondaryTrayWnd",
            )

        val isValidWindow = className !in ignoredClasses

        if (isValidWindow) {
            logger.debug { "Active window changed: title=${title ?: ""}, className=$className" }
        }

        return isValidWindow
    }
}
