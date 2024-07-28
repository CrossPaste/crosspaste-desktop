package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.AppName
import com.crosspaste.app.AppWindowManager

class DesktopNotificationManager(
    private val appWindowManager: AppWindowManager,
    private val toastManager: ToastManager,
) : NotificationManager {

    val trayState = CrossPasteTrayState()

    override fun addNotification(
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        if (appWindowManager.showMainWindow) {
            notifyToast(message, messageType, duration)
        } else {
            notifyTray(AppName, message, messageType)
        }
    }

    override fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        if (appWindowManager.showMainWindow) {
            notifyToast(message, messageType, duration)
        } else {
            notifyTray(title, message, messageType)
        }
    }

    private fun notifyToast(
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        toastManager.setToast(
            Toast(
                message = message,
                messageType = messageType,
                duration = duration,
            ),
        )
    }

    private fun notifyTray(
        title: String,
        message: String,
        messageType: MessageType,
    ) {
        trayState.sendNotification(
            Notification(
                title,
                message,
                when (messageType) {
                    MessageType.Error -> Notification.Type.Error
                    MessageType.Info -> Notification.Type.Info
                    MessageType.Success -> Notification.Type.None
                    MessageType.Warning -> Notification.Type.Warning
                },
            ),
        )
    }
}
