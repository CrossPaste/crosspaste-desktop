package com.crosspaste.ui.base

interface NotificationManager {

    fun addNotification(
        message: String,
        messageType: MessageType,
        duration: Long = 3000,
    )

    fun addNotification(
        title: String,
        message: String,
        messageType: MessageType,
        duration: Long = 3000,
    )
}
