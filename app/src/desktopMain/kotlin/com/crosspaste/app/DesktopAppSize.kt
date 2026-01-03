package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.listener.ActiveGraphicsDevice
import com.crosspaste.platform.Platform
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.contains
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point

class DesktopAppSize(
    private val platform: Platform,
) : AppSize,
    NativeMouseListener,
    ActiveGraphicsDevice {

    companion object {

        private fun createAppSizeValue(): DesktopAppSizeValue {
            // --- Basic Constants ---
            val deviceHeight: Dp = huge
            val settingsItemHeight: Dp = 40.dp
            val notificationViewMinWidth: Dp = 280.dp
            val notificationViewMaxWidth: Dp = 400.dp
            val tokenViewWidth: Dp = 320.dp

            // --- Main Window Calculation ---
            val mainMenuSize = DpSize(width = 160.dp, height = 700.dp)
            val mainContentSize = DpSize(width = 440.dp, height = 700.dp)

            val mainWindowSize =
                DpSize(
                    width = mainMenuSize.width + mainContentSize.width,
                    height = 700.dp,
                )

            val dialogWidth = 360.dp

            val windowDecorationHeight: Dp = 64.dp

            // --- Paste & QRCode ---
            val mainPasteSize = DpSize(width = 408.dp, height = 100.dp)
            val qrCodeSize = DpSize(width = 275.dp, height = 275.dp)

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
                dialogWidth = dialogWidth,
                settingsItemHeight = settingsItemHeight,
                notificationViewMinWidth = notificationViewMinWidth,
                notificationViewMaxWidth = notificationViewMaxWidth,
                tokenViewWidth = tokenViewWidth,
                // Desktop specific properties
                mainMenuSize = mainMenuSize,
                mainContentSize = mainContentSize,
                windowDecorationHeight = windowDecorationHeight,
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

    private var point: Point? = null

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

    override fun getSearchWindowState(init: Boolean): WindowState {
        val graphicsDevice = getGraphicsDevice()
        val bounds = graphicsDevice.defaultConfiguration.bounds
        val sideSearchWindowHeight = _appSizeValue.value.sideSearchWindowHeight
        val x = bounds.x.dp
        val y =
            if (init) {
                bounds.y.dp + bounds.height.dp
            } else {
                bounds.y.dp + bounds.height.dp - sideSearchWindowHeight
            }
        return WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(x, y),
            size = DpSize(width = bounds.width.dp, height = sideSearchWindowHeight),
        )
    }

    fun getPinPushEndPadding(): Dp =
        if (platform.isMacos()) {
            huge + tiny
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
    override val dialogWidth: Dp,
    override val settingsItemHeight: Dp,
    override val notificationViewMinWidth: Dp,
    override val notificationViewMaxWidth: Dp,
    override val tokenViewWidth: Dp,
    val mainMenuSize: DpSize,
    val mainContentSize: DpSize,
    val windowDecorationHeight: Dp,
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
        dialogWidth,
        settingsItemHeight,
        notificationViewMinWidth,
        notificationViewMaxWidth,
        tokenViewWidth,
    )
