package com.crosspaste.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DividerDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.platform.Platform
import com.crosspaste.ui.MenuHelper
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import com.crosspaste.utils.Memoize
import com.crosspaste.utils.contains
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle

class DesktopAppSize(
    private val configManager: DesktopConfigManager,
    private val lazyMenuHelper: Lazy<MenuHelper>,
    private val platform: Platform,
) : AppSize,
    NativeMouseListener,
    ActiveGraphicsDevice {

    val mainMenuSize: DpSize = DpSize(width = 160.dp, height = 700.dp)

    val mainContentSize: DpSize = DpSize(width = 440.dp, height = 700.dp)

    override val mainWindowSize: DpSize =
        DpSize(
            width = mainMenuSize.width + mainContentSize.width,
            height = 700.dp,
        )

    override val mainPasteSize: DpSize = DpSize(width = 408.dp, height = 100.dp)

    override val qrCodeSize: DpSize = DpSize(width = 275.dp, height = 275.dp)

    val centerSearchInputHeight: Dp = huge

    val centerSearchFooterHeight: Dp = 40.dp

    val centerSearchPasteSummaryHeight = 40.dp

    val showSearchPasteSummaryNum = 10

    val showSearchPasteSummaryVertical = small3X

    val centerSearchListViewSize: DpSize =
        DpSize(
            width = 280.dp,
            height =
                showSearchPasteSummaryNum * centerSearchPasteSummaryHeight +
                    2 * showSearchPasteSummaryVertical,
        )

    val centerSearchWindowDetailViewDpSize: DpSize = DpSize(width = 500.dp, height = 240.dp)

    val centerSearchWindowSize: DpSize =
        DpSize(
            width = centerSearchListViewSize.width + centerSearchWindowDetailViewDpSize.width,
            height = centerSearchInputHeight + centerSearchListViewSize.height + centerSearchFooterHeight,
        )

    val sideSearchWindowHeight: Dp = 332.dp

    val sideSearchInputHeight: Dp = 48.dp

    val sideSearchPaddingSize: Dp = 16.dp

    val sidePasteSize: Dp = sideSearchWindowHeight - sideSearchInputHeight - (2 * sideSearchPaddingSize)

    val grantAccessibilityPermissionsWindowsSize: DpSize = DpSize(width = 360.dp, height = 280.dp)

    override val deviceHeight: Dp = huge

    override val settingsItemHeight: Dp = 40.dp

    override val toastViewWidth: Dp = 280.dp

    override val tokenViewWidth: Dp = 320.dp

    val windowDecorationHeight: Dp = 48.dp

    val appBorderSize = tiny5X

    // Windows OS start

    val menuRoundedCornerShape = tiny2XRoundedCornerShape

    val menuWindowXOffset = 32.dp
    // Windows OS end

    private val searchCorePaddingDpSize = DpSize(zero, 100.dp)

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

    private val _menuWindowWidth: MutableStateFlow<Dp> = MutableStateFlow(150.dp)
    val menuWindowWidth: StateFlow<Dp> = _menuWindowWidth

    fun getMenuWindowHeigh(): Dp {
        val menuHelper = lazyMenuHelper.value
        val menuItemNum = menuHelper.menuItems.size + 1
        return menuItemNum * 30.dp + DividerDefaults.Thickness
    }

    fun updateMenuWindowWidth(menuWindowWidth: Dp) {
        _menuWindowWidth.value = menuWindowWidth
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

    override fun getSearchWindowState(): WindowState {
        val graphicsDevice = getGraphicsDevice()
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
                        y = bounds.y.dp + bounds.height.dp - sideSearchWindowHeight,
                    ),
                size = DpSize(width = bounds.width.dp, height = sideSearchWindowHeight),
            )
        }
    }

    fun getPinPushEndPadding(): Dp =
        if (platform.isMacos()) {
            huge
        } else if (platform.isWindows()) {
            80.dp
        } else {
            small3X
        }
}
