package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.AppName

class TestNotificationManager : NotificationManager {

    private val notifications = mutableListOf<Notification>()

    override fun addNotification(
        title: String?,
        message: String,
        messageType: MessageType,
        duration: Long,
    ) {
        notifications.add(
            Notification(
                title ?: AppName,
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
