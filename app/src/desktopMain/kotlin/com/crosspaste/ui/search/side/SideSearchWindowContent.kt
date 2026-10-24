package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.WindowsVersionHelper
import com.crosspaste.ui.NotificationHost
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.model.PluginActionsViewModel
import com.crosspaste.ui.paste.side.SidePasteboardContentView
import com.crosspaste.ui.theme.AppUIColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SideSearchWindowContent() {
    val viewModel = koinInject<PasteSelectionViewModel>()
    val pluginActionsViewModel = koinInject<PluginActionsViewModel>()
    val platform = koinInject<Platform>()

    val isMac = remember { platform.isMacos() }
    val isWindowsAndSupportBlurEffect =
        remember {
            platform.isWindows() && WindowsVersionHelper.isWindows11_22H2OrGreater
        }

    val scope = rememberCoroutineScope()

    val backgroundModifier =
        if (isMac) {
            Modifier
        } else if (isWindowsAndSupportBlurEffect) {
            Modifier.background(AppUIColors.generalBackground.copy(alpha = 0.3f))
        } else {
            Modifier.background(AppUIColors.generalBackground)
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .handlePluginPanelKeys(pluginActionsViewModel, isMac)
                .handleSideWindowKeys(viewModel, scope),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SideSearchInputView()
            SidePasteboardContentView()
        }
        PluginActionsPanelView()
        // Mount NotificationHost without window decoration offset so any notifications
        // sent while the search window is open are properly displayed.
        NotificationHost(subtractWindowDecoration = false)
    }
}

/**
 * Cmd+K (Ctrl+K elsewhere) toggles the plugin panel. While it is open it takes
 * every key before the search input or the list can, so typing never edits the
 * search behind it. Esc is the global hide-window shortcut, which closes the
 * panel before the window.
 */
private fun Modifier.handlePluginPanelKeys(
    pluginActionsViewModel: PluginActionsViewModel,
    isMac: Boolean,
): Modifier =
    onPreviewKeyEvent { event ->
        val isKeyDown = event.type == KeyEventType.KeyDown
        if (isKeyDown && event.key == Key.K && (if (isMac) event.isMetaPressed else event.isCtrlPressed)) {
            pluginActionsViewModel.toggle()
            return@onPreviewKeyEvent true
        }
        if (pluginActionsViewModel.panel.value == null) return@onPreviewKeyEvent false
        if (isKeyDown) {
            when (event.key) {
                Key.DirectionUp -> pluginActionsViewModel.selectPrev()
                Key.DirectionDown -> pluginActionsViewModel.selectNext()
                Key.Enter, Key.NumPadEnter -> pluginActionsViewModel.runSelected()
            }
        }
        true
    }

/**
 * Extracts key event logic.
 * Note: 'onKeyEvent' is used here. If you need to intercept keys before the child
 * TextField consumes them, consider using 'onPreviewKeyEvent' instead.
 */
private fun Modifier.handleSideWindowKeys(
    viewModel: PasteSelectionViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier =
    onKeyEvent { event ->
        // Only handle KeyDown events
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

        when (event.key) {
            Key.Enter -> {
                scope.launch { viewModel.toPaste() }
                true
            }
            Key.DirectionUp -> {
                viewModel.requestSearchInputFocus()
                true
            }
            Key.DirectionLeft -> {
                viewModel.selectPrev()
                true
            }
            Key.DirectionRight -> {
                viewModel.selectNext()
                true
            }
            Key.N -> {
                if (event.isCtrlPressed) {
                    viewModel.selectNext()
                    true
                } else {
                    false
                }
            }
            Key.P -> {
                if (event.isCtrlPressed) {
                    viewModel.selectPrev()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
