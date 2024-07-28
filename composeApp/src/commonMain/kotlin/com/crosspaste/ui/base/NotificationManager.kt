package com.crosspaste.ui.base

interface NotificationManager {

    fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
    )
}
