package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.WindowsVersionHelper
import com.crosspaste.ui.DesktopContext.BubbleWindowContext
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.paste.edit.PasteHtmlEditContentView
import com.crosspaste.ui.paste.edit.PasteTextEditContentView
import com.crosspaste.utils.cpuDispatcher
import com.crosspaste.utils.getPlatformUtils
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A bubble shape: rounded rectangle body with a triangular tail at the bottom,
 * pointing downward toward the search window.
 *
 * @param tailCenterFraction horizontal position of the tail center as a fraction of
 *   the bubble width (0.0 = left edge, 1.0 = right edge, 0.5 = centered).
 *   Clamped internally so the tail never overlaps the rounded corners.
 */
private class BubbleShape(
    private val cornerRadiusDp: Dp,
    private val tailWidthDp: Dp,
    private val tailHeightDp: Dp,
    private val tailCenterFraction: Float = 0.5f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cr = with(density) { cornerRadiusDp.toPx() }
        val tw = with(density) { tailWidthDp.toPx() }
        val th = with(density) { tailHeightDp.toPx() }
        val bodyBottom = size.height - th
        val w = size.width
        val halfTail = tw / 2f
        // Clamp tail center so it never overlaps the rounded corners
        val cx = (w * tailCenterFraction).coerceIn(cr + halfTail, w - cr - halfTail)

        val path =
            Path().apply {
                // Top-left corner
                moveTo(cr, 0f)
                lineTo(w - cr, 0f)
                // Top-right corner
                arcTo(Rect(w - 2 * cr, 0f, w, 2 * cr), 270f, 90f, false)
                // Right edge down
                lineTo(w, bodyBottom - cr)
                // Bottom-right corner
                arcTo(Rect(w - 2 * cr, bodyBottom - 2 * cr, w, bodyBottom), 0f, 90f, false)
                // Bottom edge, right of tail
                lineTo(cx + halfTail, bodyBottom)
                // Tail: right side → tip → left side
                lineTo(cx, size.height)
                lineTo(cx - halfTail, bodyBottom)
                // Bottom edge, left of tail
                lineTo(cr, bodyBottom)
                // Bottom-left corner
                arcTo(Rect(0f, bodyBottom - 2 * cr, 2 * cr, bodyBottom), 90f, 90f, false)
                // Left edge up
                lineTo(0f, cr)
                // Top-left corner
                arcTo(Rect(0f, 0f, 2 * cr, 2 * cr), 180f, 90f, false)
                close()
            }

        return Outline.Generic(path)
    }
}

@Composable
fun BubbleWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteDao = koinInject<PasteDao>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val platform = getPlatformUtils().platform
    val appSizeValue = LocalDesktopAppSizeValueState.current
    val density = LocalDensity.current

    val bubbleWindowInfo by appWindowManager.bubbleWindowInfo.collectAsState()
    val searchWindowInfo by appWindowManager.searchWindowInfo.collectAsState()
    val searchResults by pasteSearchViewModel.searchResults.collectAsState()

    val isMac = remember { platform.isMacos() }
    val isWindowsAndSupportBlurEffect =
        remember {
            platform.isWindows() && WindowsVersionHelper.isWindows11_22H2OrGreater
        }
    val isTransparent = isMac || isWindowsAndSupportBlurEffect

    val bubbleBodySize = appSizeValue.bubbleBodySize
    val bubbleTailHeight = appSizeValue.bubbleTailHeight
    val windowSize =
        if (isTransparent) {
            DpSize(bubbleBodySize.width, bubbleBodySize.height + bubbleTailHeight)
        } else {
            bubbleBodySize
        }
    val gap = if (isTransparent) 4.dp else 16.dp

    val logger = remember { KotlinLogging.logger("BubbleWindow") }
    val ignoreFocusLoss = remember { AtomicBoolean(true) }

    // Reactively track the target item's center-X in SearchWindow coordinates (Dp).
    // Reads LazyListState.layoutInfo which is snapshot state → recomposes on scroll.
    val itemCenterXInSearchWindow: Dp? by remember(bubbleWindowInfo.pasteId) {
        derivedStateOf {
            if (!bubbleWindowInfo.show) return@derivedStateOf null
            val listState = pasteSelectionViewModel.searchListState ?: return@derivedStateOf null

            val index = searchResults.indexOfFirst { it.id == bubbleWindowInfo.pasteId }
            if (index < 0) return@derivedStateOf null

            val itemInfo =
                listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                    ?: return@derivedStateOf null

            // Each LazyRow slot = [Spacer(sideSearchPaddingSize)] [Card(sidePasteSize)]
            // Card center = slot offset + paddingSize + cardWidth / 2
            with(density) {
                itemInfo.offset.toDp() +
                    appSizeValue.sideSearchPaddingSize +
                    appSizeValue.sidePasteSize.width / 2
            }
        }
    }

    // Tail center fraction: 0.5 = centered, shifts when bubble is clamped to screen edges
    var tailCenterFraction by remember { mutableStateOf(0.5f) }

    // Compute bubble position synchronously so the window starts at the correct location.
    // Also updates tailCenterFraction so the tail still points at the target item.
    fun computeBubblePosition(): WindowPosition {
        val searchPos = searchWindowInfo.state.position
        val searchWidth = searchWindowInfo.state.size.width
        val centerX = itemCenterXInSearchWindow
        val idealBubbleX =
            if (centerX != null) {
                searchPos.x + centerX - windowSize.width / 2
            } else {
                searchPos.x + (searchWidth - windowSize.width) / 2
            }
        // Clamp bubble X within the search window bounds
        val minX = searchPos.x
        val maxX = searchPos.x + searchWidth - windowSize.width
        val clampedBubbleX = idealBubbleX.coerceIn(minX, maxX)

        // Compute tail fraction: where should the tail point within the bubble?
        tailCenterFraction =
            if (centerX != null) {
                val itemScreenX = searchPos.x + centerX
                ((itemScreenX - clampedBubbleX) / windowSize.width).coerceIn(0.1f, 0.9f)
            } else {
                0.5f
            }

        val bubbleY = searchPos.y - windowSize.height - gap + appSizeValue.sideSearchTopBarHeight
        return WindowPosition(x = clampedBubbleX, y = bubbleY)
    }

    val windowState =
        remember {
            WindowState(
                size = windowSize,
                position = computeBubblePosition(),
            )
        }

    // Auto-hide when the target item scrolls out of view
    LaunchedEffect(itemCenterXInSearchWindow, bubbleWindowInfo.show) {
        if (bubbleWindowInfo.show && itemCenterXInSearchWindow == null) {
            // Small delay to avoid flicker during fast scroll
            delay(100)
            if (pasteSelectionViewModel.searchListState
                    ?.layoutInfo
                    ?.visibleItemsInfo
                    ?.none { it.index == searchResults.indexOfFirst { r -> r.id == bubbleWindowInfo.pasteId } } == true
            ) {
                appWindowManager.hideBubbleWindow()
            }
        }
    }

    // Reactively update position when item scrolls horizontally
    LaunchedEffect(searchWindowInfo, bubbleWindowInfo.show, itemCenterXInSearchWindow) {
        if (bubbleWindowInfo.show) {
            windowState.position = computeBubblePosition()
            windowState.size = windowSize
        }
    }

    LaunchedEffect(bubbleWindowInfo.show) {
        if (bubbleWindowInfo.show) {
            ignoreFocusLoss.set(true)
            appWindowManager.focusBubbleWindow()
            delay(1000)
        }
        ignoreFocusLoss.set(false)
    }

    Window(
        onCloseRequest = {
            appWindowManager.hideBubbleWindow()
        },
        visible = bubbleWindowInfo.show,
        state = windowState,
        title = appWindowManager.bubbleWindowTitle,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = isTransparent,
        resizable = false,
    ) {
        if (isMac) {
            MacAcrylicEffect(window = this.window)
        }

        DisposableEffect(Unit) {
            appWindowManager.bubbleComposeWindow = window

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent) {
                        logger.info { "Bubble window gained focus" }
                    }

                    override fun windowLostFocus(e: WindowEvent) {
                        if (ignoreFocusLoss.get()) {
                            logger.info { "Ignored focus loss during startup grace period" }
                            return
                        }

                        logger.info { "Bubble window lost focus" }
                        val oppositeWindow = e.oppositeWindow
                        val isSearchWindow = oppositeWindow == appWindowManager.searchComposeWindow
                        val isMainWindow = oppositeWindow == appWindowManager.mainComposeWindow
                        if (!isSearchWindow && !isMainWindow) {
                            appWindowManager.hideBubbleWindow()
                        }
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                window.removeWindowFocusListener(windowListener)
                appWindowManager.bubbleComposeWindow = null
            }
        }

        BubbleWindowContext {
            BubbleWindowContent(
                pasteId = bubbleWindowInfo.pasteId,
                pasteDao = pasteDao,
                onEscape = { appWindowManager.hideBubbleWindow() },
                isTransparent = isTransparent,
                tailCenterFraction = tailCenterFraction,
            )
        }
    }
}

@Composable
private fun BubbleWindowContent(
    pasteId: Long,
    pasteDao: PasteDao,
    onEscape: () -> Unit,
    isTransparent: Boolean,
    tailCenterFraction: Float,
) {
    val appSizeValue = LocalDesktopAppSizeValueState.current

    var pasteData by remember(pasteId) { mutableStateOf<PasteData?>(null) }
    var loading by remember(pasteId) { mutableStateOf(true) }

    LaunchedEffect(pasteId) {
        if (pasteId > 0) {
            pasteData = pasteDao.getNoDeletePasteData(pasteId)
        }
        loading = false
    }

    val cornerRadius = appSizeValue.bubbleCornerRadius
    val tailHeight = appSizeValue.bubbleTailHeight
    val shape: Shape =
        if (isTransparent) {
            remember(tailCenterFraction) {
                BubbleShape(cornerRadius, appSizeValue.bubbleTailWidth, tailHeight, tailCenterFraction)
            }
        } else {
            RoundedCornerShape(cornerRadius)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                        onEscape()
                        true
                    } else {
                        false
                    }
                }.clip(shape)
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isTransparent) Modifier.padding(bottom = tailHeight) else Modifier,
                    ).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                val data = pasteData
                if (data != null) {
                    val scope: PasteDataScope? = createPasteDataScope(data)
                    if (scope != null) {
                        val pasteType = data.getType()
                        when {
                            pasteType.isText() -> scope.PasteTextEditContentView()
                            pasteType.isHtml() -> scope.PasteHtmlEditContentView()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacAcrylicEffect(window: ComposeWindow) {
    LaunchedEffect(window) {
        snapshotFlow { window.isDisplayable }
            .first { it }

        withContext(cpuDispatcher) {
            runCatching {
                val pointer = Pointer(window.windowHandle)
                MacAppUtils.setWindowLevelScreenSaver(pointer)
            }
        }
    }
}
