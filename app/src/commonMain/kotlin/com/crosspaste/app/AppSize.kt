package com.crosspaste.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

interface AppSize {

    val mainWindowSize: DpSize

    val mainPasteSize: DpSize

    val qrCodeSize: DpSize

    val searchWindowSize: DpSize

    val searchWindowDetailViewDpSize: DpSize

    val deviceHeight: Dp

    val settingsItemHeight: Dp

    val toastViewWidth: Dp

    val windowDecorationHeight: Dp
}
