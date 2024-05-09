package com.clipevery.ui.base

import androidx.compose.runtime.Composable

interface MessageManager {

    var messageId: Int

    fun getCurrentMessageView(): (@Composable () -> Unit)?
}
