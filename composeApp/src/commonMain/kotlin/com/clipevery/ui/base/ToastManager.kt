package com.clipevery.ui.base

import androidx.compose.runtime.State

interface ToastManager {

    val toast: State<Toast?>

    fun setToast(toast: Toast)

    fun cancel()
}
