package com.clipevery.os.windows.api

import com.clipevery.app.DesktopAppWindowManager.mainWindowTitle
import com.clipevery.app.DesktopAppWindowManager.searchWindowTitle
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Psapi
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.Version
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinGDI.BITMAP
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO
import com.sun.jna.platform.win32.WinGDI.ICONINFO
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.INPUT
import com.sun.jna.platform.win32.WinUser.MONITORENUMPROC
import com.sun.jna.platform.win32.WinUser.MSG
import com.sun.jna.platform.win32.WinUser.SM_CXSCREEN
import com.sun.jna.platform.win32.WinUser.SM_CYSCREEN
import com.sun.jna.platform.win32.WinUser.SW_HIDE
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.io.File
import java.util.Optional
import javax.imageio.ImageIO

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

    fun GetForegroundWindow(): HWND?

    fun SetForegroundWindow(hWnd: HWND): Boolean

    fun GetWindowThreadProcessId(
        hWnd: HWND,
        lpdwProcessId: IntByReference,
    ): Int

    fun SendInput(
        nInputs: DWORD?,
        pInputs: Array<INPUT>?,
        cbSize: Int,
    ): Int

    fun AttachThreadInput(
        idAttach: DWORD,
        idAttachTo: DWORD,
        fAttach: Boolean,
    ): Boolean

    fun GetSystemMetrics(nIndex: Int): Int

    fun mouse_event(
        dwFlags: DWORD,
        dx: DWORD,
        dy: DWORD,
        dwData: DWORD,
        dwExtraInfo: ULONG_PTR,
    )

    fun GetIconInfo(
        hIcon: HICON,
        piconinfo: ICONINFO,
    ): Boolean

    fun GetDC(hWnd: HWND?): HDC?

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

        private var searchHWND: HWND? = null

        fun getActiveWindowProcessFilePath(): String? {
            INSTANCE.GetForegroundWindow()?.let { hwnd ->
                val processIdRef = IntByReference()
                INSTANCE.GetWindowThreadProcessId(hwnd, processIdRef)
                val processHandle =
                    Kernel32.INSTANCE.OpenProcess(
                        WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
                        false,
                        processIdRef.value,
                    )

                try {
                    val bufferSize = 1024
                    val memory = Memory((bufferSize * 2).toLong())
                    if (Psapi.INSTANCE.GetModuleFileNameEx(processHandle, null, memory, bufferSize) > 0) {
                        return memory.getWideString(0)
                    }
                } finally {
                    Kernel32.INSTANCE.CloseHandle(processHandle)
                }
            }
            return null
        }

        fun getFileDescription(filePath: String): String? {
            val intByReference = IntByReference()
            val versionLength: Int = Version.INSTANCE.GetFileVersionInfoSize(filePath, intByReference)
            if (versionLength > 0) {
                val memory = Memory(versionLength.toLong())
                if (Version.INSTANCE.GetFileVersionInfo(filePath, 0, versionLength, memory)) {
                    val pointerByReference = PointerByReference()
                    val puLen = IntByReference(0)
                    if (Version.INSTANCE.VerQueryValue(
                            memory,
                            "\\StringFileInfo\\040904b0\\FileDescription",
                            pointerByReference,
                            puLen,
                        )
                    ) {
                        return pointerByReference.value.getWideString(0)
                    }
                }
            }
            return null
        }

        fun extractAndSaveIcon(
            filePath: String,
            outputPath: String,
        ) {
            val largeIcons = arrayOfNulls<HICON>(1) // Array to receive the large icon
            val smallIcons = arrayOfNulls<HICON>(1) // Array to receive the small icon
            val iconCount = Shell32.INSTANCE.ExtractIconEx(filePath, 0, largeIcons, smallIcons, 1)

            if (iconCount > 0 && largeIcons[0] != null) {
                val icon = largeIcons[0]!!

                hiconToImage(icon)?.let { image ->
                    ImageIO.write(image, "png", File(outputPath)) // Save the icon as a PNG file
                }
            }
        }

        fun hiconToImage(hicon: HICON): BufferedImage? {
            var bitmapHandle: HBITMAP? = null
            val user32 = INSTANCE
            val gdi32 = GDI32.INSTANCE

            try {
                val info = ICONINFO()
                if (!user32.GetIconInfo(hicon, info)) return null

                info.read()
                bitmapHandle = Optional.ofNullable(info.hbmColor).orElse(info.hbmMask)

                val bitmap = BITMAP()
                if (gdi32.GetObject(bitmapHandle, bitmap.size(), bitmap.pointer) > 0) {
                    bitmap.read()

                    val width = bitmap.bmWidth.toInt()
                    val height = bitmap.bmHeight.toInt()

                    user32.GetDC(null)?.let { deviceContext ->
                        val bitmapInfo = BITMAPINFO()

                        bitmapInfo.bmiHeader.biSize = bitmapInfo.bmiHeader.size()
                        require(
                            gdi32.GetDIBits(
                                deviceContext, bitmapHandle, 0, 0, Pointer.NULL, bitmapInfo,
                                WinGDI.DIB_RGB_COLORS,
                            ) != 0,
                        ) { "GetDIBits should not return 0" }

                        bitmapInfo.read()

                        val pixels = Memory(bitmapInfo.bmiHeader.biSizeImage.toLong())
                        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB
                        bitmapInfo.bmiHeader.biHeight = -height

                        require(
                            gdi32.GetDIBits(
                                deviceContext, bitmapHandle, 0, bitmapInfo.bmiHeader.biHeight, pixels, bitmapInfo,
                                WinGDI.DIB_RGB_COLORS,
                            ) != 0,
                        ) { "GetDIBits should not return 0" }

                        val colorArray = pixels.getIntArray(0, width * height)
                        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                        image.setRGB(0, 0, width, height, colorArray, 0, width)

                        return image
                    }
                }
            } finally {
                gdi32.DeleteObject(hicon)
                Optional.ofNullable(bitmapHandle).ifPresent { hObject: HANDLE? -> gdi32.DeleteObject(hObject) }
            }

            return null
        }

        @Synchronized
        fun bringToFront(windowTitle: String): HWND? {
            INSTANCE.GetForegroundWindow()?.let { previousHwnd ->
                if (windowTitle == mainWindowTitle) {
                    return@let previousHwnd
                }

                val processIdRef = IntByReference()
                val processThreadId = INSTANCE.GetWindowThreadProcessId(previousHwnd, processIdRef)

                if (searchHWND == null) {
                    searchHWND = INSTANCE.FindWindow(null, searchWindowTitle)
                }

                if (searchHWND != null) {
                    val curThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
                    INSTANCE.AttachThreadInput(DWORD(curThreadId.toLong()), DWORD(processThreadId.toLong()), true)

                    INSTANCE.ShowWindow(searchHWND!!, SW_RESTORE)

                    val screenWidth = INSTANCE.GetSystemMetrics(SM_CXSCREEN)
                    val screenHeight = INSTANCE.GetSystemMetrics(SM_CYSCREEN)

                    INSTANCE.mouse_event(
                        DWORD(0x0001),
                        DWORD((screenWidth / 2).toLong()),
                        DWORD((screenHeight / 2).toLong()),
                        DWORD(0),
                        ULONG_PTR(0),
                    )

                    val result = INSTANCE.SetForegroundWindow(searchHWND!!)
                    INSTANCE.AttachThreadInput(DWORD(curThreadId.toLong()), DWORD(processThreadId.toLong()), false)
                    if (!result) {
                        logger.info { "Failed to set foreground window. Please switch manually" }
                    } else {
                        logger.info { "Foreground window set successfully" }
                    }
                } else {
                    logger.info { "Window not found" }
                }
                return previousHwnd
            }
            return null
        }

        fun bringToBack(
            windowTitle: String,
            previousHwnd: HWND?,
            toPaste: Boolean,
        ) {
            val hWnd = INSTANCE.FindWindow(null, windowTitle)
            if (hWnd != null) {
                INSTANCE.ShowWindow(hWnd, SW_HIDE)
            }

            if (previousHwnd != null) {
                INSTANCE.ShowWindow(previousHwnd, WinUser.SW_SHOW)
                INSTANCE.SetForegroundWindow(previousHwnd)
            }

            if (toPaste) {
                paste()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun paste() {
            val inputs: Array<INPUT> = INPUT().toArray(2) as Array<INPUT>

            inputs[0].type = DWORD(INPUT.INPUT_KEYBOARD.toLong())
            inputs[0].input.setType(
                "ki",
            )

            // Because setting INPUT_INPUT_KEYBOARD is not enough:
            // https://groups.google.com/d/msg/jna-users/NDBGwC1VZbU/cjYCQ1CjBwAJ
            inputs[0].input.ki.wScan = WORD(0)
            inputs[0].input.ki.time = DWORD(0)
            inputs[0].input.ki.dwExtraInfo = ULONG_PTR(0)

            inputs[1].type = DWORD(INPUT.INPUT_KEYBOARD.toLong())
            inputs[1].input.setType(
                "ki",
            )

            // todo Support binding dynamic shortcut keys
            inputs[1].input.ki.wScan = WORD(0)
            inputs[1].input.ki.time = DWORD(0)
            inputs[1].input.ki.dwExtraInfo = ULONG_PTR(0)

            // press ctrl
            inputs[0].input.ki.wVk = WORD(0x11)
            inputs[0].input.ki.dwFlags = DWORD(0) // keydown

            // press v
            inputs[1].input.ki.wVk = WORD('V'.code.toLong())
            inputs[1].input.ki.dwFlags = DWORD(0) // keydown

            INSTANCE.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())

            // release ctrl
            inputs[0].input.ki.wVk = WORD(0x11)
            inputs[0].input.ki.dwFlags = DWORD(2) // keyup

            // release v
            inputs[1].input.ki.wVk = WORD('V'.code.toLong())
            inputs[1].input.ki.dwFlags = DWORD(2) // keyup

            INSTANCE.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())
        }
    }
}
