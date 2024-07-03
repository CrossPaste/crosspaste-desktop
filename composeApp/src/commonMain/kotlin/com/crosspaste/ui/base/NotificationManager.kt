package com.crosspaste.ui.base

import androidx.compose.ui.window.Notification

interface NotificationManager {

    val trayState: CrossPasteTrayState

    fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
    )

    fun addNotification(notification: Notification)
}
