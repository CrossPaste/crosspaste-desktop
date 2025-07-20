package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Kernel32
import com.crosspaste.platform.windows.api.User32
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.charset.Charset

object WindowClipboard {

    private val logger = KotlinLogging.logger {}

    private val user32 = User32.INSTANCE
    private val kernel32 = Kernel32.INSTANCE

    fun supplementCFText(text: String): Boolean {
        // Open clipboard
        if (!user32.OpenClipboard(null)) {
            logger.error { "Failed to open clipboard to supplement CF_TEXT" }
            return false
        }

        return try {
            // Check if CF_TEXT format already exists
            if (user32.IsClipboardFormatAvailable(ClipboardFormats.CF_TEXT)) {
                logger.info { "Clipboard already has CF_TEXT format, skipping" }
                true
            } else {
                // Add CF_TEXT format
                val success = addAnsiText(text)
                if (success) {
                    logger.info { "Successfully supplemented CF_TEXT format" }
                    // Optional: add locale information
                    addLocale()
                } else {
                    logger.error { "Failed to add CF_TEXT format" }
                }
                success
            }
        } finally {
            // Ensure clipboard is closed
            user32.CloseClipboard()
        }
    }

    /**
     * Add ANSI text format
     */
    private fun addAnsiText(text: String): Boolean {
        return try {
            // Get system code page
            val codePage = kernel32.GetACP()
            val charsetName = getCharsetForCodePage(codePage)

            logger.info { "Using code page: $codePage ($charsetName)" }

            // Encode using system code page, add null terminator
            val data = "$text\u0000".toByteArray(Charset.forName(charsetName))

            // Allocate global memory
            val hGlobal =
                kernel32.GlobalAlloc(
                    GlobalMemoryFlags.GMEM_MOVEABLE or GlobalMemoryFlags.GMEM_SHARE,
                    data.size,
                )

            if (hGlobal == null) {
                logger.error { "Memory allocation failed" }
                return false
            }

            // Lock memory and write data
            val ptr = kernel32.GlobalLock(hGlobal)
            if (ptr == null) {
                kernel32.GlobalFree(hGlobal)
                logger.error { "Memory lock failed" }
                return false
            }

            // Write data
            ptr.write(0, data, 0, data.size)
            kernel32.GlobalUnlock(hGlobal)

            // Set to clipboard
            val result = user32.SetClipboardData(ClipboardFormats.CF_TEXT, hGlobal)
            result != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Add locale information (optional)
     */
    private fun addLocale() {
        try {
            // Check if locale information already exists
            if (user32.IsClipboardFormatAvailable(ClipboardFormats.CF_LOCALE)) {
                return
            }

            // Get current locale ID
            val lcid = kernel32.GetUserDefaultLCID()
            val lcidValue = lcid.toInt()

            // Allocate memory to store LCID (4 bytes)
            val hGlobal =
                kernel32.GlobalAlloc(
                    GlobalMemoryFlags.GMEM_MOVEABLE or GlobalMemoryFlags.GMEM_SHARE,
                    4,
                )

            if (hGlobal == null) return

            val ptr = kernel32.GlobalLock(hGlobal)
            if (ptr == null) {
                kernel32.GlobalFree(hGlobal)
                return
            }

            ptr.setInt(0, lcidValue)
            kernel32.GlobalUnlock(hGlobal)

            user32.SetClipboardData(ClipboardFormats.CF_LOCALE, hGlobal)
            logger.info { "Added locale information: $lcidValue (0x${lcidValue.toString(16)})" }
        } catch (e: Exception) {
            // Ignore locale setting errors
        }
    }

    /**
     * Get charset name based on code page
     */
    private fun getCharsetForCodePage(codePage: Int): String =
        when (codePage) {
            936 -> "GBK" // Simplified Chinese
            950 -> "Big5" // Traditional Chinese
            932 -> "Shift_JIS" // Japanese
            949 -> "EUC-KR" // Korean
            1252 -> "Windows-1252" // Western European
            65001 -> "UTF-8" // UTF-8
            else -> {
                // Try using Cp + code page number
                try {
                    val cpName = "cp$codePage"
                    // Test if this encoding is supported
                    "test".toByteArray(Charset.forName(cpName))
                    cpName
                } catch (e: Exception) {
                    "GBK" // Default to GBK
                }
            }
        }
}
