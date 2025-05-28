package com.crosspaste.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

object DesktopAppSize : AppSize {
    override val mainWindowSize: DpSize = DpSize(width = 480.dp, height = 740.dp)

    override val mainPasteSize: DpSize = DpSize(width = 424.dp, height = 100.dp)

    override val qrCodeSize: DpSize = DpSize(width = 275.dp, height = 275.dp)

    override val searchWindowSize: DpSize = DpSize(width = 800.dp, height = 540.dp)

    override val searchWindowDetailViewDpSize: DpSize = DpSize(width = 500.dp, height = 240.dp)

    override val deviceHeight: Dp = 60.dp

    override val settingsItemHeight: Dp = 40.dp

    override val toastViewWidth: Dp = 280.dp

    val appRoundedCornerShape = RoundedCornerShape(10.dp)

    val appBorderSize = 1.dp

    val mainShadowSize = 10.dp

    val mainHorizontalShadowPadding = 20.dp

    val mainTopShadowPadding = 0.dp

    val mainBottomShadowPadding = 30.dp

    val mainShadowPaddingValues =
        PaddingValues(
            start = mainHorizontalShadowPadding,
            top = mainTopShadowPadding,
            end = mainHorizontalShadowPadding,
            bottom = mainBottomShadowPadding,
        )

    // Windows OS start
    val menuWindowDpSize = DpSize(170.dp, 267.dp)

    val menuRoundedCornerShape = RoundedCornerShape(5.dp)

    val menuShadowSize = 5.dp

    val menuShadowPaddingValues = PaddingValues(10.dp, 0.dp, 10.dp, 10.dp)

    val edgePadding = 12.dp

    val menuWindowXOffset = 32.dp
    // Windows OS end

    // Mac OS start
    val mainWindowTopMargin = 30.dp
    // Mac OS end

    private val searchPaddingDpSize = DpSize(20.dp, 20.dp)

    private val searchCorePaddingDpSize = DpSize(20.dp, 120.dp)

    val searchWindowContentSize = searchWindowSize.minus(searchPaddingDpSize)

    val searchCoreContentSize = searchWindowSize.minus(searchCorePaddingDpSize)

    val searchDetailRoundedCornerShape = RoundedCornerShape(5.dp)

    val searchDetailPaddingValues = PaddingValues(10.dp)

    val searchInfoPaddingValues = PaddingValues(10.dp)
}
