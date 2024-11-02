package com.crosspaste.notification

import com.crosspaste.net.plugin.EncryptResponse.ioCoroutineDispatcher
import com.crosspaste.utils.ControlUtils.equalDebounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
abstract class NotificationManager {

    private val notificationChannel = Channel<MessageObject>()

    init {
        ioCoroutineDispatcher.launch {
            notificationChannel
                .receiveAsFlow()
                .equalDebounce(
                    durationMillis = 300,
                    isEqual = { a, b -> a.equalContent(b) },
                )
                .collect { params ->
                    doSendNotification(params)
                }
        }
    }

    fun sendNotification(messageObject: MessageObject) {
        notificationChannel.trySend(messageObject)
    }

    fun sendNotification(
        title: String? = null,
        message: String,
        messageType: MessageType,
        duration: Long = 3000,
    ) {
        sendNotification(MessageObject(title, message, messageType, duration))
    }

    abstract fun doSendNotification(messageObject: MessageObject)
}
