package com.crosspaste.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero

object DesktopAppSize : AppSize {
    override val mainWindowSize: DpSize = DpSize(width = 480.dp, height = 740.dp)

    override val mainPasteSize: DpSize = DpSize(width = 424.dp, height = 100.dp)

    override val qrCodeSize: DpSize = DpSize(width = 275.dp, height = 275.dp)

    override val searchWindowSize: DpSize = DpSize(width = 800.dp, height = 540.dp)

    override val searchWindowDetailViewDpSize: DpSize = DpSize(width = 500.dp, height = 240.dp)

    override val deviceHeight: Dp = huge

    override val settingsItemHeight: Dp = 40.dp

    override val toastViewWidth: Dp = 280.dp

    val windowDecorationHeight: Dp = huge

    val appRoundedCornerShape = small3XRoundedCornerShape

    val appBorderSize = tiny5X

    val mainShadowSize = small3X

    val mainHorizontalShadowPadding = large2X

    val mainTopShadowPadding = zero

    val mainBottomShadowPadding = xxLarge

    val mainShadowPaddingValues =
        PaddingValues(
            start = mainHorizontalShadowPadding,
            top = mainTopShadowPadding,
            end = mainHorizontalShadowPadding,
            bottom = mainBottomShadowPadding,
        )

    // Windows OS start
    val menuWindowDpSize = DpSize(170.dp, 267.dp)

    val menuRoundedCornerShape = tiny2XRoundedCornerShape

    val menuShadowSize = tiny2X

    val menuShadowPaddingValues = PaddingValues(small3X, zero, small3X, small3X)

    val edgePadding = small2X

    val menuWindowXOffset = 32.dp
    // Windows OS end

    // Mac OS start
    val mainWindowTopMargin = xxLarge
    // Mac OS end

    private val searchPaddingDpSize = DpSize(large2X, large2X)

    private val searchCorePaddingDpSize = DpSize(large2X, 120.dp)

    val searchWindowContentSize = searchWindowSize.minus(searchPaddingDpSize)

    val searchCoreContentSize = searchWindowSize.minus(searchCorePaddingDpSize)

    val searchDetailRoundedCornerShape = tiny2XRoundedCornerShape

    val searchDetailPaddingValues = PaddingValues(small3X)

    val searchInfoPaddingValues = PaddingValues(small3X)
}
