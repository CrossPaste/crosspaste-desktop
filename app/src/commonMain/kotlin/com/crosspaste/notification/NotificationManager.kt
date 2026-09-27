package com.crosspaste.notification

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.utils.GlobalCoroutineScope.ioCoroutineDispatcher
import com.crosspaste.utils.equalDebounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
abstract class NotificationManager(
    private val copywriter: GlobalCopywriter,
) {
    private val notificationChannel = Channel<Message>(Channel.BUFFERED)

    private val _notificationList: MutableStateFlow<List<Message>> = MutableStateFlow(listOf())

    val notificationList: StateFlow<List<Message>> = _notificationList

    /**
     * Slack added on top of a toast's own duration before it is dropped from
     * [notificationList], so the host UI gets to run its dismiss animation first.
     * Tests shrink it to keep the fast tier fast.
     */
    protected open val expiryGrace: Duration = 1.seconds

    fun pushNotification(toast: Message) {
        _notificationList.update { listOf(toast) + it }
        // A host may be hidden or unmounted while the toast is up, in which case
        // its own dismiss timer never runs and the toast would linger and resurface
        // the next time the host shows. Expire it here regardless.
        toast.duration?.let { duration ->
            ioCoroutineDispatcher.launch {
                delay(duration.milliseconds + expiryGrace)
                removeNotification(toast.messageId)
            }
        }
    }

    fun removeNotification(messageId: Int) {
        _notificationList.update { list -> list.filter { it.messageId != messageId } }
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
