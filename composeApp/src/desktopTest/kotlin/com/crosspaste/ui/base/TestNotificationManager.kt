package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.AppName

class TestNotificationManager : NotificationManager {

    val notifications = mutableListOf<Notification>()

    override fun addNotification(
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        notifications.add(
            Notification(
                AppName,
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

    override fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        notifications.add(
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
