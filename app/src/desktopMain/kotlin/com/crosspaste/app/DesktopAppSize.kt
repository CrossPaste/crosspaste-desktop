package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.ActiveGraphicsDevice
import com.crosspaste.platform.Platform
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.GlobalCoroutineScope.ioCoroutineDispatcher
import com.crosspaste.utils.contains
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit

class DesktopAppSize(
    private val platform: Platform,
    private val configManager: DesktopConfigManager,
) : AppSize,
    NativeMouseListener,
    ActiveGraphicsDevice {

    companion object {

        // Reasonable range around the 332dp default (roughly ±25%)
        const val MIN_SEARCH_WINDOW_HEIGHT: Int = 250
        const val MAX_SEARCH_WINDOW_HEIGHT: Int = 420

        // Gap kept between a clamped dimension and the usable screen edge, so a
        // dimension that had to be clamped never renders edge-to-edge even when the
        // platform reports no screen insets (common under XWayland, where
        // panel/taskbar insets are unavailable). Dimensions that already fit keep
        // the design size and may sit closer to the edge.
        private val SCREEN_CLAMP_MARGIN = xxLarge

        // Usable areas below this are treated as implausible platform reports
        // (headless quirks, transient display configs) rather than real screens,
        // in which case clamping is skipped entirely.
        private val MIN_PLAUSIBLE_USABLE_SIZE = 200.dp

        /**
         * The design size assumes it always fits on screen, which breaks on small or
         * heavily scaled displays (e.g. XWayland misreporting a 2.5x scale, #4759):
         * a 700dp-tall window can exceed the physical screen and gets pinned to full
         * height. Clamping against the usable screen area keeps the declared window
         * size valid regardless of what scale factor the runtime detected.
         */
        fun clampMainWindowSize(
            designSize: DpSize,
            usableScreenSize: DpSize,
        ): DpSize {
            if (usableScreenSize.width < MIN_PLAUSIBLE_USABLE_SIZE ||
                usableScreenSize.height < MIN_PLAUSIBLE_USABLE_SIZE
            ) {
                return designSize
            }
            return DpSize(
                width = minOf(designSize.width, usableScreenSize.width - SCREEN_CLAMP_MARGIN),
                height = minOf(designSize.height, usableScreenSize.height - SCREEN_CLAMP_MARGIN),
            )
        }

        // Title bar height as a fixed proportion of the square paste card,
        // matching the original 60dp title on a 252dp card at the 332dp default
        private const val SIDE_TITLE_HEIGHT_RATIO: Float = 60f / 252f

        private fun createAppSizeValue(searchWindowHeight: Int): DesktopAppSizeValue {
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

            // --- Paste ---
            val mainPasteSize = DpSize(width = 408.dp, height = 100.dp)

            // --- Side Search Calculation ---
            val sideSearchWindowHeight: Dp =
                searchWindowHeight
                    .coerceIn(MIN_SEARCH_WINDOW_HEIGHT, MAX_SEARCH_WINDOW_HEIGHT)
                    .dp
            val sideSearchTopBarHeight: Dp = 64.dp
            val sideSearchPaddingSize: Dp = 16.dp

            // --- Bubble Window ---
            val bubbleBodySize = DpSize(480.dp, 360.dp)
            val bubbleCornerRadius: Dp = 12.dp
            val bubbleTailWidth: Dp = 24.dp
            val bubbleTailHeight: Dp = 12.dp

            // Use 'run' block logic to calculate sidePasteSize
            val sidePasteSize =
                run {
                    val size = sideSearchWindowHeight - sideSearchTopBarHeight - sideSearchPaddingSize
                    DpSize(width = size, height = size)
                }

            val sideTitleHeight: Dp = sidePasteSize.height * SIDE_TITLE_HEIGHT_RATIO

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
                sideSearchTopBarHeight = sideSearchTopBarHeight,
                sideSearchPaddingSize = sideSearchPaddingSize,
                sideSearchWindowHeight = sideSearchWindowHeight,
                sideTitleHeight = sideTitleHeight,
                // Bubble window
                bubbleBodySize = bubbleBodySize,
                bubbleCornerRadius = bubbleCornerRadius,
                bubbleTailWidth = bubbleTailWidth,
                bubbleTailHeight = bubbleTailHeight,
            )
        }
    }

    private val initAppSizeValue = createAppSizeValue(configManager.config.value.searchWindowHeight)

    private val _appSizeValue: MutableStateFlow<DesktopAppSizeValue> = MutableStateFlow(initAppSizeValue)

    override val appSizeValue: StateFlow<DesktopAppSizeValue> = _appSizeValue

    init {
        ioCoroutineDispatcher.launch {
            configManager.config
                .map { it.searchWindowHeight }
                .distinctUntilChanged()
                .collect { searchWindowHeight ->
                    _appSizeValue.value = createAppSizeValue(searchWindowHeight)
                }
        }
    }

    /**
     * Synchronously applies a transient search window height (not persisted), so the
     * appearance settings can live-preview the window while the slider is dragged.
     * The value is overwritten by the config flow on the next persisted change.
     */
    fun previewSearchWindowHeight(searchWindowHeight: Int) {
        _appSizeValue.value = createAppSizeValue(searchWindowHeight)
    }

    /** Discards any preview value by recomputing sizes from the persisted config. */
    fun clearSearchWindowHeightPreview() {
        _appSizeValue.value = createAppSizeValue(configManager.config.value.searchWindowHeight)
    }

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
            size = getClampedMainWindowSize(),
            position = WindowPosition(Alignment.Center),
        )

    fun getClampedMainWindowSize(): DpSize {
        val designSize = _appSizeValue.value.mainWindowSize
        return getUsableScreenSize()
            ?.let { clampMainWindowSize(designSize, it) }
            ?: designSize
    }

    // The main window is centered via WindowPosition(Alignment.Center), which
    // resolves against the default screen, so the clamp uses the same screen.
    private fun getUsableScreenSize(): DpSize? =
        runCatching {
            val configuration =
                GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .defaultScreenDevice
                    .defaultConfiguration
            val bounds = configuration.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
            DpSize(
                width = (bounds.width - insets.left - insets.right).dp,
                height = (bounds.height - insets.top - insets.bottom).dp,
            )
        }.getOrNull()

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
            // Native Window has no Jewel leftInset compensation, so this is the
            // pin button's distance to the window's right edge directly.
            medium
        } else if (platform.isWindows()) {
            medium
        } else {
            small3X
        }
}

class DesktopAppSizeValue(
    override val mainWindowSize: DpSize,
    override val mainPasteSize: DpSize,
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
    val sideSearchTopBarHeight: Dp,
    val sideSearchPaddingSize: Dp,
    val sideSearchWindowHeight: Dp,
    val sideTitleHeight: Dp,
    val bubbleBodySize: DpSize,
    val bubbleCornerRadius: Dp,
    val bubbleTailWidth: Dp,
    val bubbleTailHeight: Dp,
) : AppSizeValue(
        mainWindowSize,
        mainPasteSize,
        deviceHeight,
        dialogWidth,
        settingsItemHeight,
        notificationViewMinWidth,
        notificationViewMaxWidth,
        tokenViewWidth,
    )
