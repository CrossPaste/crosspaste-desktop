package com.crosspaste.ui.base

import com.crosspaste.notification.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DesktopToastManager : ToastManager {

    private val _toast: MutableStateFlow<Toast?> = MutableStateFlow(null)

    override val toast: StateFlow<Toast?> = _toast

    override fun setToast(toast: Toast) {
        this._toast.value = toast
    }

    override fun cancel() {
        this._toast.value = null
    }
}
