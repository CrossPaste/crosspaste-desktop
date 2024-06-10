package com.clipevery.ui.base

import androidx.compose.ui.window.Notification

interface NotificationManager {

    val trayState: ClipeveryTrayState

    fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
    )

    fun addNotification(notification: Notification)
}
