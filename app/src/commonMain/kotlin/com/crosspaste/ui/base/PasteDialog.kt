package com.crosspaste.ui.base

import androidx.compose.runtime.Composable

interface PasteDialog {

    val key: Any

    val title: String

    @Composable
    fun content()
}
