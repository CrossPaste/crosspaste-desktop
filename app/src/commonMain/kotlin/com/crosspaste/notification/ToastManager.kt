package com.crosspaste.notification

import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ToastManager {

    private val coroutineScope = CoroutineScope(mainDispatcher)

    private val _toastList: MutableStateFlow<List<Message>> = MutableStateFlow(listOf())

    val toastList: StateFlow<List<Message>> = _toastList

    fun pushToast(toast: Message) {
        this._toastList.value = listOf(toast) + this._toastList.value

        toast.duration?.let { duration ->
            coroutineScope.launch {
                delay(duration)
                removeToast(toast.messageId)
            }
        }
    }

    fun removeToast(messageId: Int) {
        this._toastList.value = this._toastList.value.filter { it.messageId != messageId }
    }
}
