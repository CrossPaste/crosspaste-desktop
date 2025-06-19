package com.crosspaste.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listen.ActiveGraphicsDevice
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
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import com.crosspaste.utils.Memoize
import com.crosspaste.utils.contains
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle

class DesktopAppSize(
    private val configManager: DesktopConfigManager,
) : AppSize, NativeMouseListener, ActiveGraphicsDevice {
    override val mainWindowSize: DpSize = DpSize(width = 480.dp, height = 740.dp)

    override val mainPasteSize: DpSize = DpSize(width = 424.dp, height = 100.dp)

    override val qrCodeSize: DpSize = DpSize(width = 275.dp, height = 275.dp)

    val centerSearchWindowSize: DpSize = DpSize(width = 800.dp, height = 540.dp)

    val centerSearchWindowDetailViewDpSize: DpSize = DpSize(width = 500.dp, height = 240.dp)

    val dockerSearchWindowHeight: Dp = 330.dp

    val searchListViewSize: DpSize = DpSize(width = 280.dp, height = 420.dp)

    val grantAccessibilityPermissionsWindowsSize: DpSize = DpSize(width = 360.dp, height = 280.dp)

    override val deviceHeight: Dp = huge

    override val settingsItemHeight: Dp = 40.dp

    val searchFooterHeight: Dp = 40.dp

    val searchPasteTitleHeight = 40.dp

    val tabsViewHeight: Dp = 40.dp

    override val toastViewWidth: Dp = 280.dp

    override val tokenViewWidth: Dp = 320.dp

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

    val centerSearchWindowContentSize = centerSearchWindowSize.minus(searchPaddingDpSize)

    val centerSearchCoreContentSize = centerSearchWindowSize.minus(searchCorePaddingDpSize)

    val searchDetailRoundedCornerShape = tiny2XRoundedCornerShape

    val searchDetailPaddingValues = PaddingValues(small3X)

    val searchInfoPaddingValues = PaddingValues(small3X)

    private var point: Point? = null

    private val calCenterPosition: (Rectangle) -> WindowPosition =
        Memoize.memoize { bounds ->
            val windowSize = centerSearchWindowSize
            WindowPosition(
                x = (bounds.x.dp + ((bounds.width.dp - windowSize.width) / 2)),
                y = (bounds.y.dp + ((bounds.height.dp - windowSize.height) / 2)),
            )
        }

    override fun nativeMousePressed(nativeEvent: NativeMouseEvent) {
        point = nativeEvent.point
    }

    override fun getGraphicsDevice(): GraphicsDevice {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val bounds = ge.defaultScreenDevice.defaultConfiguration.bounds
        val scDevices = ge.screenDevices

        return point?.let {
            scDevices.firstOrNull { device ->
                device.contains(it, bounds.x, bounds.y)
            }
        } ?: ge.defaultScreenDevice
    }

    override fun getSearchWindowState(graphicsDevice: GraphicsDevice): WindowState {
        val bounds = graphicsDevice.defaultConfiguration.bounds
        return if (configManager.config.value.searchWindowStyle ==
            DesktopSearchWindowStyle.CENTER_STYLE.style
        ) {
            WindowState(
                placement = WindowPlacement.Floating,
                position = calCenterPosition(bounds),
                size = centerSearchWindowSize,
            )
        } else {
            WindowState(
                placement = WindowPlacement.Floating,
                position =
                    WindowPosition(
                        x = bounds.x.dp,
                        y = bounds.y.dp + bounds.height.dp - dockerSearchWindowHeight,
                    ),
                size = DpSize(width = bounds.width.dp, height = dockerSearchWindowHeight),
            )
        }
    }
}
