package com.clipevery.ui.base

import androidx.compose.ui.window.Notification

object DesktopNotificationManager : NotificationManager {

    override val trayState = ClipeveryTrayState()

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

    override fun addNotification(notification: Notification) {
        trayState.sendNotification(notification)
    }
}
