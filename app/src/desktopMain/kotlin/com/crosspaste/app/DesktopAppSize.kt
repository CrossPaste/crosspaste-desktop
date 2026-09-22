package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopAppConfig
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
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import kotlin.math.roundToInt

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

        const val PASTE_PANEL_BUTTON_SIZE_NORMAL = "normal"
        const val PASTE_PANEL_BUTTON_SIZE_SMALL = "small"

        // Gap kept between a clamped height and the usable screen edge, so the
        // window never renders edge-to-edge even when the platform reports no screen
        // insets (common under XWayland, where panel/taskbar insets are unavailable).
        private val SCREEN_CLAMP_MARGIN = xxLarge

        // Usable heights below this are treated as implausible platform reports
        // (headless quirks, transient display configs) rather than real screens,
        // in which case clamping is skipped entirely.
        private val MIN_PLAUSIBLE_USABLE_SIZE = 200.dp

        /**
         * The design size assumes it always fits on screen, which breaks on small or
         * heavily scaled displays (e.g. XWayland misreporting a 2.5x scale, #4759):
         * a 700dp-tall window can exceed the physical screen and gets pinned to full
         * height. Clamping the height against the usable screen area keeps the
         * declared window size valid regardless of what scale factor the runtime
         * detected. The width is deliberately left at the design value: the
         * two-column 600dp layout does not reflow, and height overflow is the
         * failure mode observed in practice.
         */
        fun clampMainWindowSize(
            designSize: DpSize,
            usableScreenSize: DpSize,
        ): DpSize {
            if (usableScreenSize.height < MIN_PLAUSIBLE_USABLE_SIZE) {
                return designSize
            }
            return DpSize(
                width = designSize.width,
                height =
                    minOf(
                        designSize.height,
                        usableScreenSize.height - SCREEN_CLAMP_MARGIN,
                    ),
            )
        }

        // Title bar height as a fixed proportion of the square paste card,
        // matching the original 60dp title on a 252dp card at the 332dp default
        private const val SIDE_TITLE_HEIGHT_RATIO: Float = 60f / 252f

        private fun createAppSizeValue(
            searchWindowHeight: Int,
            pastePanelButtonSize: String,
        ): DesktopAppSizeValue {
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

            // --- Paste Panel ---
            val pastePanelSize = DpSize(300.dp, 420.dp)
            val pastePanelRowHeight: Dp = 44.dp
            val pastePanelButtonSize: Dp =
                if (pastePanelButtonSize == PASTE_PANEL_BUTTON_SIZE_SMALL) 36.dp else 48.dp

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
                // Paste panel
                pastePanelSize = pastePanelSize,
                pastePanelRowHeight = pastePanelRowHeight,
                pastePanelButtonSize = pastePanelButtonSize,
                // Bubble window
                bubbleBodySize = bubbleBodySize,
                bubbleCornerRadius = bubbleCornerRadius,
                bubbleTailWidth = bubbleTailWidth,
                bubbleTailHeight = bubbleTailHeight,
            )
        }
    }

    private val initAppSizeValue = createAppSizeValue(configManager.config.value)

    private val _appSizeValue: MutableStateFlow<DesktopAppSizeValue> = MutableStateFlow(initAppSizeValue)

    override val appSizeValue: StateFlow<DesktopAppSizeValue> = _appSizeValue

    init {
        ioCoroutineDispatcher.launch {
            configManager.config
                .map { it.searchWindowHeight to it.pastePanelButtonSize }
                .distinctUntilChanged()
                .collect { (searchWindowHeight, pastePanelButtonSize) ->
                    _appSizeValue.value = createAppSizeValue(searchWindowHeight, pastePanelButtonSize)
                }
        }
    }

    /**
     * Synchronously applies a transient search window height (not persisted), so the
     * appearance settings can live-preview the window while the slider is dragged.
     * The value is overwritten by the config flow on the next persisted change.
     */
    fun previewSearchWindowHeight(searchWindowHeight: Int) {
        _appSizeValue.value =
            createAppSizeValue(searchWindowHeight, configManager.config.value.pastePanelButtonSize)
    }

    /** Discards any preview value by recomputing sizes from the persisted config. */
    fun clearSearchWindowHeightPreview() {
        _appSizeValue.value = createAppSizeValue(configManager.config.value)
    }

    private fun createAppSizeValue(config: DesktopAppConfig): DesktopAppSizeValue =
        createAppSizeValue(config.searchWindowHeight, config.pastePanelButtonSize)

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

    /**
     * Floating paste panel button: docked to the right edge of the active display,
     * vertically centred in the usable area (menu bar / taskbar excluded).
     */
    fun getPastePanelButtonWindowState(): WindowState {
        val usable = usableBounds(getGraphicsDevice().defaultConfiguration)
        val size = _appSizeValue.value.pastePanelButtonSize
        val x = usable.right - size - medium
        val y = usable.top + (usable.height - size) / 2
        return WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(x, y),
            size = DpSize(size, size),
        )
    }

    /** Paste panel beside the floating button, kept on the display the button is on. */
    fun getPastePanelWindowState(button: WindowState): WindowState {
        val buttonRect =
            DpRect(
                origin = DpOffset(button.position.x, button.position.y),
                size = button.size,
            )
        val center =
            Point(
                (buttonRect.left + buttonRect.width / 2).value.roundToInt(),
                (buttonRect.top + buttonRect.height / 2).value.roundToInt(),
            )
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val configuration =
            ge.screenDevices
                .map { it.defaultConfiguration }
                .firstOrNull { it.bounds.contains(center) }
                ?: ge.defaultScreenDevice.defaultConfiguration
        val size = _appSizeValue.value.pastePanelSize
        val position = pastePanelPositionBeside(buttonRect, size, usableBounds(configuration), medium)
        return WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(position.x, position.y),
            size = size,
        )
    }

    private fun usableBounds(configuration: GraphicsConfiguration): DpRect {
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        return DpRect(
            left = (bounds.x + insets.left).dp,
            top = (bounds.y + insets.top).dp,
            right = (bounds.x + bounds.width - insets.right).dp,
            bottom = (bounds.y + bounds.height - insets.bottom).dp,
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
    val pastePanelSize: DpSize,
    val pastePanelRowHeight: Dp,
    val pastePanelButtonSize: Dp,
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

/**
 * Where the paste panel goes for a button at [button]: to its left when that fits inside
 * [screen], otherwise to its right; top-aligned with the button and clamped so the whole
 * panel stays on screen.
 */
internal fun pastePanelPositionBeside(
    button: DpRect,
    panel: DpSize,
    screen: DpRect,
    gap: Dp,
): DpOffset {
    val leftOfButton = button.left - gap - panel.width
    val x = if (leftOfButton >= screen.left) leftOfButton else button.right + gap
    val maxX = maxOf(screen.left, screen.right - panel.width)
    val maxY = maxOf(screen.top, screen.bottom - panel.height)
    return DpOffset(
        x = x.coerceIn(screen.left, maxX),
        y = button.top.coerceIn(screen.top, maxY),
    )
}
