package com.crosspaste.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ToastManager {

    private val _toastList: MutableStateFlow<List<Message>> = MutableStateFlow(listOf())

    val toastList: StateFlow<List<Message>> = _toastList

    fun pushToast(toast: Message) {
        this._toastList.value = listOf(toast) + this._toastList.value
    }

    fun removeToast(messageId: Int) {
        this._toastList.value = this._toastList.value.filter { it.messageId != messageId }
    }
}
