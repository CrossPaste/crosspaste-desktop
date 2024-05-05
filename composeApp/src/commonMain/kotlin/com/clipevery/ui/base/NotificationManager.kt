package com.clipevery.ui.base

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState

interface NotificationManager {

    val trayState: TrayState

    fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
    )

    fun addNotification(notification: Notification)
}
