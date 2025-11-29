package com.crosspaste.platform.windows.api

import com.crosspaste.app.WinAppInfo
import com.crosspaste.platform.windows.JIconExtract
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Psapi
import com.sun.jna.platform.win32.Version
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
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
import com.sun.jna.platform.win32.WinUser.SW_HIDE
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.Optional
import javax.imageio.ImageIO

interface User32 : com.sun.jna.platform.win32.User32 {
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

    fun SetClipboardViewer(hWndNewViewer: HWND?): HWND?

    fun ChangeClipboardChain(
        hWndRemove: HWND?,
        hWndNewNext: HWND?,
    ): Boolean

    fun MsgWaitForMultipleObjects(
        nCount: Int,
        pHandles: Array<HANDLE?>?,
        bWaitAll: Boolean,
        dwMilliseconds: Int,
        dwWakeMask: Int,
    ): Int

    fun GetClipboardSequenceNumber(): Int

    fun EnumWindows(
        lpEnumFunc: WndEnumProc,
        lParam: Pointer?,
    ): Boolean

    fun IsIconic(hWnd: HWND): Boolean

    fun GetWindowTextLengthW(hWnd: HWND): Int

    fun GetWindowTextW(
        hWnd: HWND,
        lpString: CharArray,
        nMaxCount: Int,
    ): Int

    fun OpenClipboard(hWndNewOwner: HWND?): Boolean

    fun CloseClipboard(): Boolean

    fun EmptyClipboard(): Boolean

    fun SetClipboardData(
        uFormat: Int,
        hMem: HANDLE?,
    ): HANDLE?

    fun GetClipboardData(uFormat: Int): HANDLE?

    fun IsClipboardFormatAvailable(format: Int): Boolean

    fun EnumClipboardFormats(format: Int): Int

    fun GetClipboardFormatName(
        format: Int,
        lpszFormatName: CharArray,
        cchMaxCount: Int,
    ): Int

    fun GetClassNameW(
        hWnd: HWND,
        lpClassName: CharArray,
        nMaxCount: Int,
    ): Int

    companion object {
        val INSTANCE =
            Native.load(
                "user32",
                User32::class.java,
                DEFAULT_OPTIONS + mapOf("allow-get-last-error" to true),
            ) as User32
        const val GWL_WNDPROC = -4
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

        const val EVENT_SYSTEM_FOREGROUND = 0x0003
        const val WINEVENT_OUTOFCONTEXT = 0x0000

        private val logger = KotlinLogging.logger {}

        fun getActiveWindowProcessFilePath(): String? =
            INSTANCE.GetForegroundWindow()?.let { hwnd ->
                val processIdRef = IntByReference()
                INSTANCE.GetWindowThreadProcessId(hwnd, processIdRef)
                val processHandle =
                    Kernel32.INSTANCE.OpenProcess(
                        WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
                        false,
                        processIdRef.value,
                    )

                runCatching {
                    val bufferSize = 1024
                    val memory = Memory((bufferSize * 2).toLong())
                    if (Psapi.INSTANCE.GetModuleFileNameEx(
                            processHandle,
                            null,
                            memory,
                            bufferSize,
                        ) > 0
                    ) {
                        memory.getWideString(0)
                    } else {
                        null
                    }
                }.apply {
                    Kernel32.INSTANCE.CloseHandle(processHandle)
                }.getOrNull()
            }

        fun getFileDescription(filePath: String): String? {
            val intByReference = IntByReference()
            val versionLength: Int =
                Version.INSTANCE.GetFileVersionInfoSize(filePath, intByReference)
            if (versionLength > 0) {
                val memory = Memory(versionLength.toLong())
                val lplpTranslate = PointerByReference()
                if (Version.INSTANCE.GetFileVersionInfo(filePath, 0, versionLength, memory)) {
                    val puLen = IntByReference()
                    if (Version.INSTANCE.VerQueryValue(
                            memory,
                            "\\VarFileInfo\\Translation",
                            lplpTranslate,
                            puLen,
                        )
                    ) {
                        val array: IntArray = lplpTranslate.value.getIntArray(0L, puLen.value / 4)
                        val langAndCodepage =
                            findLangAndCodepage(
                                array,
                            ) ?: return null
                        val l: Int = langAndCodepage and 0xFFFF
                        val m: Int = (langAndCodepage and -65536) shr 16

                        val lang = String.format(Locale.ROOT, "%04x", l).takeLast(4)
                        val codepage = String.format(Locale.ROOT, "%04x", m).takeLast(4)
                        val lpSubBlock =
                            String.format(
                                Locale.ROOT,
                                "\\StringFileInfo\\$lang$codepage\\FileDescription",
                                l,
                                m,
                            )

                        val lplpBuffer = PointerByReference()
                        if (Version.INSTANCE.VerQueryValue(
                                memory,
                                lpSubBlock,
                                lplpBuffer,
                                puLen,
                            )
                        ) {
                            if (puLen.value > 0) {
                                return lplpBuffer.value.getWideString(0)
                            }
                        } else {
                            logger.error { "FileDescription GetLastError ${Kernel32.INSTANCE.GetLastError()}" }
                        }
                    } else {
                        logger.error { "Translation GetLastError ${Kernel32.INSTANCE.GetLastError()}" }
                    }
                }
            }
            return null
        }

        private fun findLangAndCodepage(array: IntArray): Int? {
            var value: Int? = null
            for (i in array) {
                if ((i and -65536) == 78643200 && (i and 65535) == 1033) {
                    return i
                }
                value = i
            }

            return value
        }

        fun extractAndSaveIcon(
            filePath: String,
            outputPath: String,
        ) {
            JIconExtract.getIconForFile(filePath)?.let { icon ->
                ImageIO.write(icon, "png", File(outputPath))
                return@extractAndSaveIcon
            }

            val largeIcons = arrayOfNulls<HICON>(1)
            val smallIcons = arrayOfNulls<HICON>(1)
            val iconCount =
                Shell32.INSTANCE.ExtractIconEx(
                    filePath,
                    0,
                    largeIcons,
                    smallIcons,
                    1,
                )

            if (iconCount > 0 && largeIcons[0] != null) {
                val icon = largeIcons[0]!!

                hiconToImage(icon)?.let { image ->
                    ImageIO.write(image, "png", File(outputPath))
                }
            }
        }

        private fun hiconToImage(hicon: HICON): BufferedImage? {
            var bitmapHandle: HBITMAP? = null
            val user32 = INSTANCE
            val gdi32 = GDI32.INSTANCE

            return runCatching {
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
                                deviceContext,
                                bitmapHandle,
                                0,
                                0,
                                Pointer.NULL,
                                bitmapInfo,
                                WinGDI.DIB_RGB_COLORS,
                            ) != 0,
                        ) { "GetDIBits should not return 0" }

                        bitmapInfo.read()

                        val pixels = Memory(bitmapInfo.bmiHeader.biSizeImage.toLong())
                        bitmapInfo.bmiHeader.biCompression = WinGDI.BI_RGB
                        bitmapInfo.bmiHeader.biHeight = -height

                        require(
                            gdi32.GetDIBits(
                                deviceContext,
                                bitmapHandle,
                                0,
                                bitmapInfo.bmiHeader.biHeight,
                                pixels,
                                bitmapInfo,
                                WinGDI.DIB_RGB_COLORS,
                            ) != 0,
                        ) { "GetDIBits should not return 0" }

                        val colorArray = pixels.getIntArray(0, width * height)
                        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                        image.setRGB(0, 0, width, height, colorArray, 0, width)

                        image
                    }
                } else {
                    null
                }
            }.apply {
                gdi32.DeleteObject(hicon)
                Optional
                    .ofNullable(bitmapHandle)
                    .ifPresent { hObject: HANDLE? -> gdi32.DeleteObject(hObject) }
            }.getOrNull()
        }

        fun getForegroundWindowAppInfoAndThreadId(useShortcutKeys: Boolean): WinAppInfo? {
            val hwnd =
                if (useShortcutKeys) {
                    getForegroundWindow()
                } else {
                    getForegroundWindowByEnumWindows()
                }

            return hwnd?.let { previousHwnd ->
                val processIdRef = IntByReference()
                val threadId =
                    INSTANCE.GetWindowThreadProcessId(
                        previousHwnd,
                        processIdRef,
                    )

                val processHandle =
                    Kernel32.INSTANCE.OpenProcess(
                        WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
                        false,
                        processIdRef.value,
                    )

                runCatching {
                    val bufferSize = 1024
                    val memory = Memory((bufferSize * 2).toLong())
                    if (Psapi.INSTANCE.GetModuleFileNameEx(
                            processHandle,
                            null,
                            memory,
                            bufferSize,
                        ) > 0
                    ) {
                        val filePath = memory.getWideString(0)
                        WinAppInfo(previousHwnd, filePath, threadId)
                    } else {
                        null
                    }
                }.apply {
                    Kernel32.INSTANCE.CloseHandle(processHandle)
                }.getOrNull()
            }
        }

        private fun getForegroundWindowByEnumWindows(): HWND? {
            var foregroundHwnd: HWND? = null
            INSTANCE.EnumWindows(
                object : WndEnumProc {
                    override fun callback(
                        hWnd: HWND,
                        lParam: Pointer?,
                    ): Boolean {
                        // Check if the window is visible
                        if (!INSTANCE.IsWindowVisible(hWnd)) {
                            return true
                        }

                        // Check if the window is minimized (iconic)
                        if (INSTANCE.IsIconic(hWnd)) {
                            return true
                        }

                        // Check if it is a top-level window (no parent or parent is the desktop)
                        val parent = INSTANCE.GetParent(hWnd)
                        if (parent != null && parent != INSTANCE.GetDesktopWindow()) {
                            return true
                        }

                        // Get the window title
                        val titleLength = INSTANCE.GetWindowTextLengthW(hWnd)
                        if (titleLength == 0) {
                            return true
                        }

                        val buffer = CharArray(titleLength + 1)
                        INSTANCE.GetWindowTextW(hWnd, buffer, titleLength + 1)
                        val title = Native.toString(buffer).trim()

                        if (title.isEmpty()) {
                            return true
                        }

                        // Retrieve process information to filter out Explorer
                        val processIdRef = IntByReference()
                        INSTANCE.GetWindowThreadProcessId(hWnd, processIdRef)

                        val processHandle =
                            Kernel32.INSTANCE.OpenProcess(
                                WinNT.PROCESS_QUERY_INFORMATION or WinNT.PROCESS_VM_READ,
                                false,
                                processIdRef.value,
                            )

                        if (processHandle != null) {
                            try {
                                val bufferSize = 1024
                                val memory = Memory((bufferSize * 2).toLong())
                                if (Psapi.INSTANCE.GetModuleFileNameEx(
                                        processHandle,
                                        null,
                                        memory,
                                        bufferSize,
                                    ) > 0
                                ) {
                                    val filePath = memory.getWideString(0)
                                    // Check if the process is Explorer.exe
                                    if (filePath.lowercase().endsWith("explorer.exe")) {
                                        return true
                                    }
                                }
                            } finally {
                                Kernel32.INSTANCE.CloseHandle(processHandle)
                            }
                        }

                        // Found the first window that meets all conditions
                        foregroundHwnd = hWnd
                        return false
                    }
                },
                null,
            )
            return foregroundHwnd
        }

        private fun getForegroundWindow(): HWND? = INSTANCE.GetForegroundWindow()

        @Synchronized
        fun bringToFront(
            prevThreadId: Int?,
            searchWindow: HWND?,
        ) {
            val handle = searchWindow ?: return

            val targetThreadId =
                if (prevThreadId != null) {
                    INSTANCE.GetWindowThreadProcessId(handle, IntByReference())
                } else {
                    0
                }

            val shouldAttach = prevThreadId != null
            if (shouldAttach) {
                val success =
                    INSTANCE.AttachThreadInput(
                        DWORD(prevThreadId.toLong()),
                        DWORD(targetThreadId.toLong()),
                        true,
                    )
                if (!success) {
                    logger.error { "AttachThreadInput failed: ${Kernel32.INSTANCE.GetLastError()}" }
                    return
                }
            }

            try {
                INSTANCE.ShowWindow(handle, SW_RESTORE)
                INSTANCE.BringWindowToTop(handle)
                if (INSTANCE.SetForegroundWindow(handle)) {
                    logger.info { "Foreground window set successfully" }
                } else {
                    logger.info { "Failed to set foreground window. Please switch manually" }
                }
            } finally {
                if (shouldAttach) {
                    INSTANCE.AttachThreadInput(
                        DWORD(prevThreadId.toLong()),
                        DWORD(targetThreadId.toLong()),
                        false,
                    )
                }
            }
        }

        fun backToBack(
            backWindow: HWND?,
            previousHwnd: HWND?,
        ) {
            backWindow?.let { hwnd ->
                INSTANCE.ShowWindow(hwnd, SW_HIDE)
            }

            previousHwnd?.let { hwnd ->
                INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOW)
                INSTANCE.SetForegroundWindow(hwnd)
            }
        }

        fun bringToBackAndPaste(
            backWindow: HWND?,
            previousHwnd: HWND?,
            keyCodes: List<Int>,
        ) {
            backToBack(backWindow, previousHwnd)
            paste(keyCodes)
        }

        @Suppress("UNCHECKED_CAST")
        fun paste(keyCodes: List<Int>) {
            if (keyCodes.isEmpty()) {
                return
            }

            val inputs: Array<INPUT> = INPUT().toArray(keyCodes.size) as Array<INPUT>

            for (i in keyCodes.indices) {
                inputs[i].type = DWORD(INPUT.INPUT_KEYBOARD.toLong())
                inputs[i].input.setType(
                    "ki",
                )

                // Because setting INPUT_INPUT_KEYBOARD is not enough:
                // https://groups.google.com/d/msg/jna-users/NDBGwC1VZbU/cjYCQ1CjBwAJ
                inputs[i].input.ki.wScan = WORD(0)
                inputs[i].input.ki.time = DWORD(0)
                inputs[i].input.ki.dwExtraInfo = ULONG_PTR(0)

                inputs[i].input.ki.wVk = WORD(keyCodes[i].toLong())
                inputs[i].input.ki.dwFlags = DWORD(0) // keydown
            }

            INSTANCE.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())

            for (i in keyCodes.indices) {
                inputs[i].input.ki.dwFlags = DWORD(2) // keyup
            }

            INSTANCE.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())
        }

        fun findPasteWindow(windowTitle: String): HWND? =
            INSTANCE.FindWindow(null, windowTitle)?.also { hwnd ->
                // Set the window icon not to be displayed on the taskbar
                val style =
                    INSTANCE.GetWindowLong(
                        hwnd,
                        WinUser.GWL_EXSTYLE,
                    )
                INSTANCE.SetWindowLong(
                    hwnd,
                    WinUser.GWL_EXSTYLE,
                    style or 0x00000080,
                )
            }

        fun isInstalledFromMicrosoftStore(path: Path): Boolean {
            val windowsAppsPath: Path = Paths.get("C:\\Program Files\\WindowsApps").toAbsolutePath()
            return path.startsWith(windowsAppsPath)
        }
    }
}
