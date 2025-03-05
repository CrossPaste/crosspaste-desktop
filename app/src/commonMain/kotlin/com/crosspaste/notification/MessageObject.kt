package com.crosspaste.notification

data class MessageObject(
    val title: String? = null,
    val message: String,
    val messageType: MessageType,
    val duration: Long? = 3000,
) {

    fun equalContent(other: MessageObject): Boolean {
        return title == other.title && message == other.message && messageType == other.messageType
    }
}
