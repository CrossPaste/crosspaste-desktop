package com.crosspaste.platform.windows

import com.crosspaste.paste.WindowsClipboardFormatProbe
import com.crosspaste.platform.windows.api.Kernel32
import com.crosspaste.platform.windows.api.User32
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * JNA-backed [WindowsClipboardFormatProbe]. Format names are registered once
 * and cached; [isFormatAvailable] never opens the clipboard, [readDword] opens
 * it briefly and retries a few times if another process still holds it.
 */
internal class Win32ClipboardFormatProbe(
    private val user32: User32 = User32.INSTANCE,
    private val kernel32: Kernel32 = Kernel32.INSTANCE,
) : WindowsClipboardFormatProbe {

    private val logger = KotlinLogging.logger {}

    private val formatIds = ConcurrentHashMap<String, Int>()

    private fun formatId(name: String): Int = formatIds.getOrPut(name) { user32.RegisterClipboardFormatA(name) }

    override fun isFormatAvailable(name: String): Boolean {
        val id = formatId(name)
        return id != 0 && user32.IsClipboardFormatAvailable(id)
    }

    override fun readDword(name: String): Int? {
        val id = formatId(name)
        if (id == 0) return null
        if (!openClipboardWithRetry()) {
            logger.debug { "Could not open clipboard to read format $name" }
            return null
        }
        return try {
            val handle = user32.GetClipboardData(id) ?: return null
            if (kernel32.GlobalSize(handle) < Int.SIZE_BYTES) return null
            val pointer = kernel32.GlobalLock(handle) ?: return null
            try {
                pointer.getInt(0)
            } finally {
                kernel32.GlobalUnlock(handle)
            }
        } finally {
            user32.CloseClipboard()
        }
    }

    private fun openClipboardWithRetry(): Boolean {
        repeat(OPEN_ATTEMPTS) { attempt ->
            if (user32.OpenClipboard(null)) return true
            if (attempt < OPEN_ATTEMPTS - 1) Thread.sleep(OPEN_RETRY_INTERVAL_MS)
        }
        return false
    }

    private companion object {
        const val OPEN_ATTEMPTS = 5
        const val OPEN_RETRY_INTERVAL_MS = 10L
    }
}
