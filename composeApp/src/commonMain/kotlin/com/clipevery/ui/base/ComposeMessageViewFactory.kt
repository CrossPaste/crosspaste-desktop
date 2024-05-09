package com.clipevery.ui.base

import androidx.compose.runtime.Composable

interface ComposeMessageViewFactory {

    var showMessage: Boolean

    @Composable
    fun MessageView(key: Any)
}
