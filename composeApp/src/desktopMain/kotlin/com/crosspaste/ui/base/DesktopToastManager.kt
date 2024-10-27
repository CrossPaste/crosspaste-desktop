package com.crosspaste.ui.base

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.crosspaste.notification.ToastManager

class DesktopToastManager : ToastManager {

    private var _toast: MutableState<Toast?> = mutableStateOf(null)

    override val toast: State<Toast?> get() = _toast

    override fun setToast(toast: Toast) {
        this._toast.value = toast
    }

    override fun cancel() {
        this._toast.value = null
    }
}
