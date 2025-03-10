package com.crosspaste.notification

import kotlinx.coroutines.flow.StateFlow

interface ToastManager {

    val toastList: StateFlow<List<Toast>>

    fun pushToast(toast: Toast)

    fun removeToast(messageId: Int)
}
