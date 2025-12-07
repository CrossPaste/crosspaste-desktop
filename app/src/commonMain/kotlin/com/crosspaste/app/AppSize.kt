package com.crosspaste.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.StateFlow

interface AppSize {

    val appSizeValue: StateFlow<AppSizeValue>
}

open class AppSizeValue(
    open val mainWindowSize: DpSize,
    open val mainPasteSize: DpSize,
    open val qrCodeSize: DpSize,
    open val deviceHeight: Dp,
    open val settingsItemHeight: Dp,
    open val toastViewWidth: Dp,
    open val tokenViewWidth: Dp,
)
