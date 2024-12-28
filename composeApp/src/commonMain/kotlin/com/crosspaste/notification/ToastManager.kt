package com.crosspaste.notification

import com.crosspaste.ui.base.Toast
import kotlinx.coroutines.flow.StateFlow

interface ToastManager {

    val toast: StateFlow<Toast?>

    fun setToast(toast: Toast)

    fun cancel()
}
