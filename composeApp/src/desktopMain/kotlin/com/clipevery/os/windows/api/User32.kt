package com.clipevery.os.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.INPUT
import com.sun.jna.platform.win32.WinUser.KEYBDINPUT.KEYEVENTF_KEYUP
import com.sun.jna.platform.win32.WinUser.MONITORENUMPROC
import com.sun.jna.platform.win32.WinUser.MSG
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS
import io.github.oshai.kotlinlogging.KotlinLogging

interface User32 : StdCallLibrary {
    interface WNDPROC : StdCallCallback {
        fun callback(
            hWnd: HWND?,
            uMsg: Int,
            uParam: WPARAM?,
            lParam: LPARAM?,
        ): Int
    }

    fun SetWindowLongPtr(
        hWnd: HWND?,
        nIndex: Int,
        proc: WNDPROC?,
    ): Int

    fun SetWindowLong(
        hWnd: HWND?,
        nIndex: Int,
        proc: WNDPROC?,
    ): Int

    fun CreateWindowEx(
        styleEx: Int,
        className: String?,
        windowName: String?,
        style: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        hndParent: HWND?,
        hndMenu: Int,
        hndInst: Int,
        param: Any?,
    ): HWND?

    fun DestroyWindow(hwnd: HWND?): Int

    fun SetClipboardViewer(hWndNewViewer: HWND?): HWND?

    fun ChangeClipboardChain(
        hWndRemove: HWND?,
        hWndNewNext: HWND?,
    ): Boolean

    fun PeekMessage(
        lpMsg: MSG?,
        hWnd: HWND?,
        wMsgFilterMin: Int,
        wMsgFilterMax: Int,
        wRemoveMsg: Int,
    ): Boolean

    fun TranslateMessage(lpMsg: MSG?): Boolean

    fun DispatchMessage(lpMsg: MSG?): Int

    fun MsgWaitForMultipleObjects(
        nCount: Int,
        pHandles: Array<HANDLE?>?,
        bWaitAll: Boolean,
        dwMilliseconds: Int,
        dwWakeMask: Int,
    ): Int

    fun SendMessage(
        hWnd: HWND?,
        message: Int,
        wParam: WPARAM?,
        lParam: LPARAM?,
    )

    fun DefWindowProc(
        hWnd: HWND?,
        msg: Int,
        wParam: WPARAM?,
        lParam: LPARAM?,
    ): Int

    fun EnumDisplayMonitors(
        hdc: HDC?,
        lprcClip: RECT?,
        lpfnEnum: MONITORENUMPROC?,
        dwData: LPARAM?,
    ): Boolean

    fun GetClipboardSequenceNumber(): Int

    fun FindWindow(
        lpClassName: String?,
        lpWindowName: String?,
    ): HWND?

    fun ShowWindow(
        hWnd: HWND,
        nCmdShow: Int,
    ): Boolean

    fun GetForegroundWindow(): HWND

    fun SetForegroundWindow(hWnd: HWND): Boolean

    fun EnumWindows(
        lpEnumFunc: WinUser.WNDENUMPROC,
        lParam: Pointer?,
    ): Boolean

    fun GetWindowThreadProcessId(
        hWnd: HWND,
        lpdwProcessId: IntByReference,
    ): Int

    fun SetActiveWindow(hWnd: HWND): HWND?

    fun SendInput(
        nInputs: DWORD?,
        pInputs: Array<INPUT?>?,
        cbSize: Int,
    ): Int

    fun AttachThreadInput(
        idAttach: DWORD,
        idAttachTo: DWORD,
        fAttach: Boolean,
    ): Boolean

    fun SetWindowPos(
        hWnd: HWND,
        hWndInsertAfter: Int,
        X: Int,
        Y: Int,
        cx: Int,
        cy: Int,
        uFlags: Int,
    ): Boolean

    fun SendInput(
        nInputs: DWORD,
        pInputs: INPUT.ByReference,
        cbSize: Int,
    ): DWORD

    companion object {
        val INSTANCE =
            Native.load(
                "user32",
                User32::class.java,
                DEFAULT_OPTIONS,
            ) as User32
        const val GWL_EXSTYLE = -20
        const val GWL_STYLE = -16
        const val GWL_WNDPROC = -4
        const val GWL_HINSTANCE = -6
        const val GWL_ID = -12
        const val GWL_USERDATA = -21
        const val DWL_DLGPROC = 4
        const val DWL_MSGRESULT = 0
        const val DWL_USER = 8
        const val WS_EX_COMPOSITED = 0x20000000
        const val WS_EX_LAYERED = 0x80000
        const val WS_EX_TRANSPARENT = 32
        const val WM_DESTROY = 0x0002
        const val WM_CHANGECBCHAIN = 0x030D
        const val WM_DRAWCLIPBOARD = 0x0308

        /**
         * PeekMessage() Options
         */
        const val PM_NOREMOVE = 0x0000
        const val PM_REMOVE = 0x0001
        const val PM_NOYIELD = 0x0002
        const val QS_KEY = 0x0001
        const val QS_MOUSEMOVE = 0x0002
        const val QS_MOUSEBUTTON = 0x0004
        const val QS_POSTMESSAGE = 0x0008
        const val QS_TIMER = 0x0010
        const val QS_PAINT = 0x0020
        const val QS_SENDMESSAGE = 0x0040
        const val QS_HOTKEY = 0x0080
        const val QS_ALLPOSTMESSAGE = 0x0100
        const val QS_RAWINPUT = 0x0400
        const val QS_TOUCH = 0x0800
        const val QS_POINTER = 0x1000
        const val QS_MOUSE = QS_MOUSEMOVE or QS_MOUSEBUTTON
        const val QS_INPUT = QS_MOUSE or QS_KEY or QS_RAWINPUT
        const val QS_ALLEVENTS = QS_INPUT or QS_POSTMESSAGE or QS_TIMER or QS_PAINT or QS_HOTKEY
        const val QS_ALLINPUT = (
            QS_INPUT or QS_POSTMESSAGE or QS_TIMER or QS_PAINT
                or QS_HOTKEY or QS_SENDMESSAGE
        )

        private val logger = KotlinLogging.logger {}

        fun hideWindowByTitle(windowTitle: String) {
            val hWnd = INSTANCE.FindWindow(null, windowTitle)
            if (hWnd != null) {
                INSTANCE.ShowWindow(hWnd, 0) // 0 表示 SW_HIDE
            }
        }

        fun bringToFrontAndReturnPreviousAppId(windowTitle: String): Int {
            val previousHwnd = INSTANCE.GetForegroundWindow()
            val processIdRef = IntByReference()
            val processThreadId = INSTANCE.GetWindowThreadProcessId(previousHwnd, processIdRef)
            val hWnd = INSTANCE.FindWindow(null, windowTitle)
            if (hWnd != null) {
                val curThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
                INSTANCE.AttachThreadInput(DWORD(curThreadId.toLong()), DWORD(processThreadId.toLong()), true)

                INSTANCE.ShowWindow(hWnd, SW_RESTORE)

                val win = INSTANCE.SetActiveWindow(hWnd)

                if (win != null) {
                    logger.info { "Window activated" }
                } else {
                    logger.info { "Window not activated" }
                }

//                INSTANCE.SetWindowPos(hWnd, -1, 0, 0, 0, 0, SWP_NOSIZE or SWP_NOMOVE)
//                INSTANCE.SetWindowPos(hWnd, -2, 0, 0, 0, 0, SWP_NOSIZE or SWP_NOMOVE)
                val result = INSTANCE.SetForegroundWindow(hWnd)
                INSTANCE.AttachThreadInput(DWORD(curThreadId.toLong()), DWORD(processThreadId.toLong()), false)
                if (!result) {
                    logger.info { "Failed to set foreground window. Please switch manually" }
                } else {
                    logger.info { "Foreground window set successfully" }
                }
            } else {
                logger.info { "Window not found" }
            }

            return processIdRef.value
        }

        fun activateAppAndPaste(
            processId: Int,
            toPaste: Boolean,
        ) {
            // 定义回调函数，用于查找与进程ID匹配的窗口
            val callback =
                WinUser.WNDENUMPROC { hWnd, _ ->
                    val processIdRef = IntByReference()

                    INSTANCE.GetWindowThreadProcessId(hWnd, processIdRef)
                    if (processIdRef.value == processId) {
                        INSTANCE.SetForegroundWindow(hWnd)
                        INSTANCE.ShowWindow(hWnd, WinUser.SW_SHOW)
                        if (toPaste) {
                            INSTANCE.SendMessage(hWnd, 0x0302, null, null)
                        }
                        false // 停止枚举
                    } else {
                        true // 继续枚举
                    }
                }

            INSTANCE.EnumWindows(callback, null)
        }

        fun simulatePaste() {
            val inputStructure =
                INPUT().apply {
                    input.setType("ki")
                    input.ki.wVk = WinDef.WORD(0x56) // 'V' key
                    input.ki.wScan = WinDef.WORD(0)
                    input.ki.time = DWORD(0)
                    input.ki.dwExtraInfo = BaseTSD.ULONG_PTR()
                }

            val ctrlDown =
                inputStructure.apply {
                    input.ki.wVk = WinDef.WORD(0x11)
                } // CTRL key
            val ctrlUp =
                inputStructure.apply {
                    input.ki.dwFlags = DWORD(KEYEVENTF_KEYUP.toLong())
                }

            // Send the key strokes
            INSTANCE.SendInput(DWORD(1), arrayOf(ctrlDown), inputStructure.size())
            INSTANCE.SendInput(DWORD(1), arrayOf(inputStructure), inputStructure.size()) // 'V'
            INSTANCE.SendInput(
                DWORD(1),
                arrayOf(
                    inputStructure.apply {
                        input.ki.dwFlags = DWORD(KEYEVENTF_KEYUP.toLong())
                    },
                ),
                inputStructure.size(),
            ) // Release 'V'
            INSTANCE.SendInput(DWORD(1), arrayOf(ctrlUp), inputStructure.size()) // Release CTRL
        }
    }
}
