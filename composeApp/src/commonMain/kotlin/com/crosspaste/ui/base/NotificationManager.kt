package com.crosspaste.ui.base

interface NotificationManager {

    fun addNotification(
        title: String? = null,
        message: String,
        messageType: MessageType,
        duration: Long = 3000,
    )
}
