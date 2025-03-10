package com.crosspaste.ui.base

import com.crosspaste.notification.Toast
import com.crosspaste.notification.ToastManager
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DesktopToastManager : ToastManager {

    private val coroutineScope = CoroutineScope(mainDispatcher)

    private val _toastList: MutableStateFlow<List<Toast>> = MutableStateFlow(listOf())

    override val toastList: StateFlow<List<Toast>> = _toastList

    override fun pushToast(toast: Toast) {
        this._toastList.value = listOf(toast) + this._toastList.value

        toast.duration?.let { duration ->
            coroutineScope.launch {
                delay(duration)
                removeToast(toast.messageId)
            }
        }
    }

    override fun removeToast(messageId: Int) {
        this._toastList.value = this._toastList.value.filter { it.messageId != messageId }
    }
}
