package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification
import com.crosspaste.app.AppName
import com.crosspaste.notification.MessageObject
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager

class TestNotificationManager : NotificationManager() {

    private val notifications = mutableListOf<Notification>()

    override fun doSendNotification(messageObject: MessageObject) {
        notifications.add(
            Notification(
                messageObject.title ?: AppName,
                messageObject.message,
                when (messageObject.messageType) {
                    MessageType.Error -> Notification.Type.Error
                    MessageType.Info -> Notification.Type.Info
                    MessageType.Success -> Notification.Type.None
                    MessageType.Warning -> Notification.Type.Warning
                },
            ),
        )
    }
}
