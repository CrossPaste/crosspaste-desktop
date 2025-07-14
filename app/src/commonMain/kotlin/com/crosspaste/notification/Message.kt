package com.crosspaste.notification

data class Message(
    val messageId: Int,
    val title: String,
    val message: String? = null,
    val messageType: MessageType,
    val duration: Long? = 3000,
) {

    fun equalContent(other: Message): Boolean =
        messageType == other.messageType &&
            message == other.message &&
            title == other.title
}
