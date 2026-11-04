package com.crosspaste.paste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager

/**
 * One-time macOS notice for users who sync Apple devices through both Universal
 * Clipboard and CrossPaste: each copy then reaches the other device twice, and
 * CrossPaste's write of it is relayed back by Universal Clipboard. Nothing written
 * to the pasteboard stops that relay, so the user is asked to keep one channel.
 * Shown on the first Universal Clipboard content seen while an Apple device is paired.
 */
class UniversalClipboardHint(
    private val configManager: DesktopConfigManager,
    private val notificationManager: NotificationManager,
    private val syncManager: SyncManager,
) {

    fun onUniversalClipboardContent() {
        if (!configManager.getCurrentConfig().showUniversalClipboardHint) return
        val hasApplePeer =
            syncManager.getSyncHandlers().values.any { handler ->
                handler.currentSyncRuntimeInfo.platform.isApple()
            }
        if (!hasApplePeer) return

        configManager.updateConfig("showUniversalClipboardHint", false)
        notificationManager.sendNotification(
            title = { it.getText("universal_clipboard_hint_title") },
            message = { it.getText("universal_clipboard_hint_message") },
            messageType = MessageType.Info,
            duration = null,
        )
    }
}
