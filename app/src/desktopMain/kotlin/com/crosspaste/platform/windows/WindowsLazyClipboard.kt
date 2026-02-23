package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Kernel32
import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class WindowsLazyClipboard : AutoCloseable {

    private val logger = KotlinLogging.logger {}

    private val user32 = User32.INSTANCE
    private val kernel32 = Kernel32.INSTANCE
    private val getMessageW = Function.getFunction("user32", "GetMessageW", Function.ALT_CONVENTION)

    private val windowDispatcher = newSingleThreadContext("WindowsLazyClipboard")
    private val scope = CoroutineScope(windowDispatcher + SupervisorJob())

    private var hwnd: HWND? = null
    private var messageLoopJob: Job? = null
    private val ready = CompletableDeferred<Unit>()

    @Volatile
    private var fileCount: Int = 0

    @Volatile
    private var resolver: ((Int, Pointer, Int) -> Int)? = null
    private val _provideDataCallCount = AtomicInteger(0)
    val provideDataCallCount: Int get() = _provideDataCallCount.get()

    private var crossPasteFormatId: Int = 0

    @Volatile
    private var writeDeferred: CompletableDeferred<Int>? = null

    private val wndProc =
        User32.WNDPROC { hWnd, uMsg, wParam, lParam ->
            handleMessage(hWnd, uMsg, wParam, lParam)
        }

    fun start() {
        messageLoopJob =
            scope.launch {
                hwnd =
                    user32.CreateWindowEx(
                        0,
                        "STATIC",
                        "CrossPasteLazyClipboard",
                        0,
                        0,
                        0,
                        0,
                        0,
                        null,
                        0,
                        0,
                        null,
                    )

                if (hwnd == null) {
                    ready.completeExceptionally(
                        IllegalStateException("Failed to create hidden window for lazy clipboard"),
                    )
                    return@launch
                }

                user32.SetWindowLongPtr(hwnd, User32.GWL_WNDPROC, wndProc)

                crossPasteFormatId = user32.RegisterClipboardFormatA("CrossPaste")
                logger.info { "Registered CrossPaste clipboard format: $crossPasteFormatId" }

                ready.complete(Unit)

                val msg = WinUser.MSG()
                // GetMessageW returns int: >0 = message, 0 = WM_QUIT, -1 = error
                while (true) {
                    val result = getMessageW.invokeInt(arrayOf(msg, null, 0, 0))
                    if (result <= 0) break
                    user32.TranslateMessage(msg)
                    user32.DispatchMessage(msg)
                }
            }

        runBlocking {
            withTimeout(5000) { ready.await() }
        }
    }

    override fun close() {
        hwnd?.let { user32.PostMessage(it, WM_CLOSE, null, null) }
        runBlocking {
            withTimeoutOrNull(3000) { messageLoopJob?.join() }
        }
        scope.cancel()
        windowDispatcher.close()
        hwnd = null
    }

    fun writeFilesToClipboard(
        count: Int,
        resolver: (Int, Pointer, Int) -> Int,
    ): Int {
        if (count <= 0) return -1
        val w = hwnd ?: return -1

        this.fileCount = count
        this.resolver = resolver
        val deferred = CompletableDeferred<Int>()
        writeDeferred = deferred

        user32.PostMessage(w, WM_WRITE_FILES, null, null)
        return runBlocking {
            withTimeoutOrNull(5000) { deferred.await() } ?: -1
        }
    }

    fun resetProvideDataCallCount() {
        _provideDataCallCount.set(0)
    }

    private fun handleMessage(
        hWnd: HWND?,
        uMsg: Int,
        wParam: WPARAM?,
        lParam: LPARAM?,
    ): Int {
        when (uMsg) {
            WM_WRITE_FILES -> {
                val result = doWriteFilesToClipboard()
                writeDeferred?.complete(result)
                return 0
            }
            User32.WM_RENDERFORMAT -> {
                val format = wParam?.toInt() ?: 0
                if (format == ClipboardFormats.CF_HDROP) {
                    renderHDrop()
                }
                return 0
            }
            User32.WM_RENDERALLFORMATS -> {
                if (user32.OpenClipboard(hwnd)) {
                    renderHDrop()
                    user32.CloseClipboard()
                }
                return 0
            }
            WM_CLOSE -> {
                user32.DestroyWindow(hWnd)
                return 0
            }
            User32.WM_DESTROY -> {
                user32.PostQuitMessage(0)
                return 0
            }
        }
        return user32.DefWindowProc(hWnd, uMsg, wParam, lParam).toInt()
    }

    private fun doWriteFilesToClipboard(): Int {
        val w = hwnd ?: return -1

        if (!user32.OpenClipboard(w)) {
            logger.error { "Failed to open clipboard" }
            return -1
        }

        return try {
            user32.EmptyClipboard()

            // Set eager CrossPaste marker
            val markerMem =
                kernel32.GlobalAlloc(
                    GlobalMemoryFlags.GMEM_MOVEABLE or GlobalMemoryFlags.GMEM_ZEROINIT,
                    1,
                )
            if (markerMem != null) {
                user32.SetClipboardData(crossPasteFormatId, markerMem)
            }

            // Delayed rendering for CF_HDROP
            user32.SetClipboardData(ClipboardFormats.CF_HDROP, null)

            user32.GetClipboardSequenceNumber()
        } finally {
            user32.CloseClipboard()
        }
    }

    private fun renderHDrop() {
        val currentResolver = resolver ?: return
        val count = fileCount
        if (count <= 0) return

        val buffer = Memory(4096)
        val paths = mutableListOf<String>()

        for (i in 0..count) {
            val len = currentResolver(i, buffer, 4096)
            if (len < 0) {
                logger.error { "Resolver returned error for index $i" }
                return
            }
            val pathBytes = ByteArray(len)
            buffer.read(0, pathBytes, 0, len)
            paths.add(String(pathBytes, Charsets.UTF_8))
        }

        // Build DROPFILES structure
        val dropFilesHeaderSize = 20
        // Encode each path as UTF-16LE with null terminator, plus final double-null
        val encodedPaths =
            paths.map { path ->
                val utf16 = path.toByteArray(Charsets.UTF_16LE)
                // path bytes + 2 bytes for null terminator
                utf16 + byteArrayOf(0, 0)
            }
        val pathsSize = encodedPaths.sumOf { it.size } + 2 // final double-null terminator
        val totalSize = dropFilesHeaderSize + pathsSize

        val hMem =
            kernel32.GlobalAlloc(
                GlobalMemoryFlags.GMEM_MOVEABLE or GlobalMemoryFlags.GMEM_ZEROINIT,
                totalSize,
            )
        if (hMem == null) {
            logger.error { "Failed to allocate memory for DROPFILES" }
            return
        }

        val ptr = kernel32.GlobalLock(hMem)
        if (ptr == null) {
            kernel32.GlobalFree(hMem)
            logger.error { "Failed to lock memory for DROPFILES" }
            return
        }

        // Write DROPFILES header
        ptr.setInt(0, dropFilesHeaderSize) // pFiles: offset to file list
        ptr.setInt(4, 0) // pt.x
        ptr.setInt(8, 0) // pt.y
        ptr.setInt(12, 0) // fNC
        ptr.setInt(16, 1) // fWide = 1 (Unicode)

        // Write file paths
        var offset = dropFilesHeaderSize.toLong()
        for (encoded in encodedPaths) {
            ptr.write(offset, encoded, 0, encoded.size)
            offset += encoded.size
        }
        // Double-null terminator (already zeroed by GMEM_ZEROINIT, but be explicit)
        ptr.setShort(offset, 0)

        kernel32.GlobalUnlock(hMem)

        user32.SetClipboardData(ClipboardFormats.CF_HDROP, hMem)
        _provideDataCallCount.incrementAndGet()
    }

    companion object {
        private const val WM_CLOSE = 0x0010
        private const val WM_WRITE_FILES = 0x0400 + 1 // WM_USER + 1
    }
}
