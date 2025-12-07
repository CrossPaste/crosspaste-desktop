package com.crosspaste.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.ActiveGraphicsDevice
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.MenuHelper
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

    companion object {

        private fun createAppSizeValue(): DesktopAppSizeValue {
            // --- Basic Constants ---
            val deviceHeight: Dp = huge
            val settingsItemHeight: Dp = 40.dp
            val toastViewWidth: Dp = 280.dp
            val tokenViewWidth: Dp = 320.dp
            val appBorderSize: Dp = tiny5X

            // --- Main Window Calculation ---
            val mainMenuSize = DpSize(width = 160.dp, height = 700.dp)
            val mainContentSize = DpSize(width = 440.dp, height = 700.dp)

            val mainWindowSize =
                DpSize(
                    width = mainMenuSize.width + mainContentSize.width,
                    height = 700.dp,
                )

            val windowDecorationHeight: Dp = 48.dp

            // --- Paste & QRCode ---
            val mainPasteSize = DpSize(width = 408.dp, height = 100.dp)
            val qrCodeSize = DpSize(width = 275.dp, height = 275.dp)

            // --- Center Search Calculation ---
            val centerSearchInputHeight: Dp = huge
            val centerSearchFooterHeight: Dp = 40.dp
            val centerSearchPasteSummaryHeight: Dp = 40.dp
            val showSearchPasteSummaryNum = 10
            val showSearchPasteSummaryVertical: Dp = small3X

            // Calculate List View size
            val centerSearchListViewSize =
                DpSize(
                    width = 280.dp,
                    height =
                        (centerSearchPasteSummaryHeight * showSearchPasteSummaryNum) +
                            (showSearchPasteSummaryVertical * 2),
                )

            val centerSearchWindowDetailViewDpSize = DpSize(width = 500.dp, height = 240.dp)

            // Calculate total Center Window size
            val centerSearchWindowSize =
                DpSize(
                    width = centerSearchListViewSize.width + centerSearchWindowDetailViewDpSize.width,
                    height = centerSearchInputHeight + centerSearchListViewSize.height + centerSearchFooterHeight,
                )

            // Calculate Core Content Size (exclude padding)
            val searchCorePaddingDpSize = DpSize(width = zero, height = 100.dp)
            val centerSearchCoreContentSize = centerSearchWindowSize - searchCorePaddingDpSize

            val centerSearchDetailRoundedCornerShape = tiny2XRoundedCornerShape
            val centerSearchDetailPaddingValues = PaddingValues(small3X)
            val centerSearchInfoPaddingValues = PaddingValues(small3X)

            // --- Side Search Calculation ---
            val sideSearchWindowHeight: Dp = 332.dp
            val sideSearchInputHeight: Dp = 48.dp
            val sideSearchPaddingSize: Dp = 16.dp
            val sideTitleHeight: Dp = huge

            // Use 'run' block logic to calculate sidePasteSize
            val sidePasteSize =
                run {
                    val size = sideSearchWindowHeight - sideSearchInputHeight - (sideSearchPaddingSize * 2)
                    DpSize(width = size, height = size)
                }

            val sidePasteContentSize =
                DpSize(
                    width = sidePasteSize.width,
                    height = sidePasteSize.height - sideTitleHeight,
                )

            // --- Build and return the object ---
            return DesktopAppSizeValue(
                // Base properties
                mainWindowSize = mainWindowSize,
                mainPasteSize = mainPasteSize,
                qrCodeSize = qrCodeSize,
                deviceHeight = deviceHeight,
                settingsItemHeight = settingsItemHeight,
                toastViewWidth = toastViewWidth,
                tokenViewWidth = tokenViewWidth,
                // Desktop specific properties
                appBorderSize = appBorderSize,
                mainMenuSize = mainMenuSize,
                mainContentSize = mainContentSize,
                windowDecorationHeight = windowDecorationHeight,
                centerSearchCoreContentSize = centerSearchCoreContentSize,
                centerSearchDetailRoundedCornerShape = centerSearchDetailRoundedCornerShape,
                centerSearchDetailPaddingValues = centerSearchDetailPaddingValues,
                centerSearchInfoPaddingValues = centerSearchInfoPaddingValues,
                centerSearchInputHeight = centerSearchInputHeight,
                centerSearchFooterHeight = centerSearchFooterHeight,
                centerSearchListViewSize = centerSearchListViewSize,
                centerSearchPasteSummaryHeight = centerSearchPasteSummaryHeight,
                centerSearchWindowDetailViewDpSize = centerSearchWindowDetailViewDpSize,
                centerSearchWindowSize = centerSearchWindowSize,
                sidePasteContentSize = sidePasteContentSize,
                sidePasteSize = sidePasteSize,
                sideSearchInputHeight = sideSearchInputHeight,
                sideSearchPaddingSize = sideSearchPaddingSize,
                sideSearchWindowHeight = sideSearchWindowHeight,
                sideTitleHeight = sideTitleHeight,
            )
        }
    }

    private val initAppSizeValue = createAppSizeValue()

    private val _appSizeValue: MutableStateFlow<DesktopAppSizeValue> = MutableStateFlow(initAppSizeValue)

    override val appSizeValue: StateFlow<DesktopAppSizeValue> = _appSizeValue

    val menuWindowXOffset = 32.dp

    private var point: Point? = null

    private val calCenterPosition: (Rectangle) -> WindowPosition =
        Memoize.memoize { bounds ->
            val windowSize = _appSizeValue.value.centerSearchWindowSize
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

    override fun getMainWindowState(): WindowState =
        WindowState(
            isMinimized = false,
            size = _appSizeValue.value.mainWindowSize,
            position = WindowPosition(Alignment.Center),
        )

    override fun getSearchWindowState(): WindowState {
        val graphicsDevice = getGraphicsDevice()
        val bounds = graphicsDevice.defaultConfiguration.bounds
        return if (configManager.config.value.searchWindowStyle ==
            DesktopSearchWindowStyle.CENTER_STYLE.style
        ) {
            WindowState(
                placement = WindowPlacement.Floating,
                position = calCenterPosition(bounds),
                size = _appSizeValue.value.centerSearchWindowSize,
            )
        } else {
            val sideSearchWindowHeight = _appSizeValue.value.sideSearchWindowHeight
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

class DesktopAppSizeValue(
    override val mainWindowSize: DpSize,
    override val mainPasteSize: DpSize,
    override val qrCodeSize: DpSize,
    override val deviceHeight: Dp,
    override val settingsItemHeight: Dp,
    override val toastViewWidth: Dp,
    override val tokenViewWidth: Dp,
    val appBorderSize: Dp,
    val mainMenuSize: DpSize,
    val mainContentSize: DpSize,
    val windowDecorationHeight: Dp,
    val centerSearchCoreContentSize: DpSize,
    val centerSearchDetailRoundedCornerShape: RoundedCornerShape,
    val centerSearchDetailPaddingValues: PaddingValues,
    val centerSearchInfoPaddingValues: PaddingValues,
    val centerSearchInputHeight: Dp,
    val centerSearchFooterHeight: Dp,
    val centerSearchListViewSize: DpSize,
    val centerSearchPasteSummaryHeight: Dp,
    val centerSearchWindowDetailViewDpSize: DpSize,
    val centerSearchWindowSize: DpSize,
    val sidePasteContentSize: DpSize,
    val sidePasteSize: DpSize,
    val sideSearchInputHeight: Dp,
    val sideSearchPaddingSize: Dp,
    val sideSearchWindowHeight: Dp,
    val sideTitleHeight: Dp,
) : AppSizeValue(
        mainWindowSize,
        mainPasteSize,
        qrCodeSize,
        deviceHeight,
        settingsItemHeight,
        toastViewWidth,
        tokenViewWidth,
    )
