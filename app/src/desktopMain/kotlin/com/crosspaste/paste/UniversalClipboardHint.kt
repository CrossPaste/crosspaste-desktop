package com.crosspaste.paste

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationCenter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager

/**
 * One-time macOS notice for users who sync Apple devices through both Universal
 * Clipboard and CrossPaste: each copy then reaches the other device twice, and
 * CrossPaste's write of it is relayed back by Universal Clipboard. Nothing written
 * to the pasteboard stops that relay, so the user is asked to keep one channel.
 * Saved on the first Universal Clipboard content seen while an Apple device is
 * paired, so it stays readable in the Notification Center after the toast is gone.
 */
class UniversalClipboardHint(
    private val notificationCenter: NotificationCenter,
    private val notificationManager: NotificationManager,
    private val syncManager: SyncManager,
) {

    suspend fun onUniversalClipboardContent() {
        if (!hasApplePeer()) return

        // save() reports false once the notice was saved before, including across
        // restarts and after the user cleared the center: the delivery key outlives
        // the entry, so a cleared hint stays cleared.
        val saved =
            notificationCenter
                .save(
                    titleKey = TITLE_KEY,
                    messageKey = MESSAGE_KEY,
                    messageType = MessageType.Info,
                    onceKey = ONCE_KEY,
                ).getOrElse { return }
        if (!saved) return

        notificationManager.sendNotification(
            title = { it.getText(TITLE_KEY) },
            message = { it.getText(MESSAGE_KEY) },
            messageType = MessageType.Info,
        )
    }

    private fun hasApplePeer(): Boolean =
        syncManager.getSyncHandlers().values.any { handler ->
            handler.currentSyncRuntimeInfo.platform.isApple()
        }

    private companion object {
        const val TITLE_KEY = "universal_clipboard_hint_title"
        const val MESSAGE_KEY = "universal_clipboard_hint_message"
        const val ONCE_KEY = "universal_clipboard_hint"
    }
}
