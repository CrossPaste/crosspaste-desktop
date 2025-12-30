package com.crosspaste.notification

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.utils.GlobalCoroutineScope.ioCoroutineDispatcher
import com.crosspaste.utils.equalDebounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
abstract class NotificationManager(
    private val copywriter: GlobalCopywriter,
) {
    private val notificationChannel = Channel<Message>()

    private val _notificationList: MutableStateFlow<List<Message>> = MutableStateFlow(listOf())

    val notificationList: StateFlow<List<Message>> = _notificationList

    fun pushNotification(toast: Message) {
        this._notificationList.value = listOf(toast) + this._notificationList.value
    }

    fun removeNotification(messageId: Int) {
        this._notificationList.value = this._notificationList.value.filter { it.messageId != messageId }
    }

    init {
        ioCoroutineDispatcher.launch {
            notificationChannel
                .receiveAsFlow()
                .equalDebounce(
                    durationMillis = 300,
                    isEqual = { a, b -> a.equalContent(b) },
                ).collect { params ->
                    doSendNotification(params)
                }
        }
    }

    protected fun sendNotification(message: Message) {
        notificationChannel.trySend(message)
    }

    abstract fun getMessageId(): Int

    fun sendNotification(
        title: (GlobalCopywriter) -> String,
        message: ((GlobalCopywriter) -> String)? = null,
        messageType: MessageType,
        duration: Long? = 3000,
    ) {
        sendNotification(
            Message(
                messageId = getMessageId(),
                title = title(copywriter),
                message = message?.let { it(copywriter) },
                messageType = messageType,
                duration = duration,
            ),
        )
    }

    abstract fun doSendNotification(message: Message)
}
