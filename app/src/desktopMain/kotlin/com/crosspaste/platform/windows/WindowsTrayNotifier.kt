package com.crosspaste.platform.windows

import com.crosspaste.notification.MessageType
import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Shows a notification as a balloon on our tray icon; Windows 10/11 present it as a
 * toast. Sound is left to SoundService, which already plays one for errors and successes.
 */
internal class WindowsTrayNotifier(
    private val user32: User32,
    private val shell32: Shell32,
    private val processId: Int,
) {
    private val logger = KotlinLogging.logger {}

    /** False when the tray icon is missing or the shell refused the balloon. */
    fun notify(
        title: String,
        message: String,
        messageType: MessageType,
    ): Boolean {
        val owner =
            findTrayIconOwner(user32, processId) ?: run {
                logger.info { "No tray icon to show a balloon on" }
                return false
            }
        // An empty szInfo removes the balloon instead of showing it, so a title-only
        // message (e.g. "copy successful") is shown as the body
        val (infoTitle, info) = if (message.isEmpty()) "" to title else title to message
        val data =
            Shell32.NotifyIconData().apply {
                hWnd = owner
                uID = TRAY_ICON_ID
                uFlags = NIF_INFO
                fill(szInfoTitle, infoTitle)
                fill(szInfo, info)
                dwInfoFlags = infoIcon(messageType) or NIIF_NOSOUND
            }
        return shell32.Shell_NotifyIcon(NIM_MODIFY, data).also { shown ->
            if (!shown) logger.info { "Shell refused the tray balloon" }
        }
    }

    companion object {
        const val NIM_MODIFY = 0x1
        const val NIF_INFO = 0x10
        const val NIIF_NONE = 0x0
        const val NIIF_INFO = 0x1
        const val NIIF_WARNING = 0x2
        const val NIIF_ERROR = 0x3
        const val NIIF_NOSOUND = 0x10

        fun infoIcon(messageType: MessageType): Int =
            when (messageType) {
                MessageType.Error -> NIIF_ERROR
                MessageType.Warning -> NIIF_WARNING
                MessageType.Info -> NIIF_INFO
                MessageType.Success -> NIIF_NONE
            }

        /**
         * Copies [text] into a fixed WCHAR buffer, truncated so a terminating NUL always fits
         * and a surrogate pair is never split.
         */
        fun fill(
            buffer: CharArray,
            text: String,
        ) {
            var length = minOf(text.length, buffer.size - 1)
            if (length < text.length && length > 0 && text[length - 1].isHighSurrogate()) length--
            text.toCharArray(buffer, 0, 0, length)
            buffer.fill('\u0000', length)
        }
    }
}
