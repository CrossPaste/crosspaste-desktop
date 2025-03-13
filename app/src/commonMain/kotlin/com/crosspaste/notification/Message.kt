package com.crosspaste.notification

import com.crosspaste.i18n.GlobalCopywriter

data class Message(
    val messageId: Int,
    val title: (GlobalCopywriter) -> String,
    val message: ((GlobalCopywriter) -> String)? = null,
    val messageType: MessageType,
    val duration: Long? = 3000,
) {

    fun equalContent(other: Message): Boolean {
        return messageType == other.messageType &&
            message == other.message &&
            title == other.title
    }
}
