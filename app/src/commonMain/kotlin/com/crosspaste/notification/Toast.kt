package com.crosspaste.notification

data class Toast(
    val messageId: Int,
    val messageType: MessageType,
    val message: String,
    val duration: Long? = 3000,
)
