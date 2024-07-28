package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification

object DesktopNotificationManager : NotificationManager {

    val trayState = CrossPasteTrayState()

    override fun addNotification(
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
