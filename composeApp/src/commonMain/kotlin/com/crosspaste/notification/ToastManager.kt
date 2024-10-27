package com.crosspaste.notification

import androidx.compose.runtime.State
import com.crosspaste.ui.base.Toast

interface ToastManager {

    val toast: State<Toast?>

    fun setToast(toast: Toast)

    fun cancel()
}
