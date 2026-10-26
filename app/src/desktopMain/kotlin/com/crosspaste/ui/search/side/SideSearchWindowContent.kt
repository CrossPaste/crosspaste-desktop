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
import com.crosspaste.ui.base.ProvideSystemDoubleClickInterval
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.side.SidePasteboardContentView
import com.crosspaste.ui.theme.AppUIColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SideSearchWindowContent() {
    val viewModel = koinInject<PasteSelectionViewModel>()
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

    ProvideSystemDoubleClickInterval {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(backgroundModifier)
                    .handleSideWindowKeys(viewModel, scope),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SideSearchInputView()
                SidePasteboardContentView()
            }
            // Mount NotificationHost without window decoration offset so any notifications
            // sent while the search window is open are properly displayed.
            NotificationHost(subtractWindowDecoration = false)
        }
    }
}

/**
 * Routes every search window shortcut through [resolveSearchWindowKey]: the preview pass runs
 * before the focused child, the bubble pass after it declined the key.
 */
private fun Modifier.handleSideWindowKeys(
    viewModel: PasteSelectionViewModel,
    scope: CoroutineScope,
): Modifier =
    onPreviewKeyEvent { viewModel.dispatchKey(KeyPhase.PREVIEW, it, scope) }
        .onKeyEvent { viewModel.dispatchKey(KeyPhase.BUBBLE, it, scope) }

private fun PasteSelectionViewModel.dispatchKey(
    phase: KeyPhase,
    event: KeyEvent,
    scope: CoroutineScope,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val stroke =
        SearchKeyStroke(
            key = event.key,
            isCtrlPressed = event.isCtrlPressed,
            isShiftPressed = event.isShiftPressed,
            isAltPressed = event.isAltPressed,
            isMetaPressed = event.isMetaPressed,
        )
    val action =
        resolveSearchWindowKey(phase, focusedElement.value, stroke, isSearchCaretAtEnd)
            ?: return false
    when (action) {
        SearchWindowKeyAction.PASTE -> scope.launch { toPaste() }
        SearchWindowKeyAction.SELECT_PREV -> selectPrev()
        SearchWindowKeyAction.SELECT_NEXT -> selectNext()
        SearchWindowKeyAction.HIGHLIGHT_PREV_TAG -> highlightPrevTag()
        SearchWindowKeyAction.HIGHLIGHT_NEXT_TAG -> highlightNextTag()
        SearchWindowKeyAction.ACTIVATE_TAG -> activateHighlightedTag()
        SearchWindowKeyAction.FOCUS_SEARCH_INPUT -> requestSearchInputFocus()
        SearchWindowKeyAction.FOCUS_TAG_BAR -> requestTagBarFocus()
        SearchWindowKeyAction.FOCUS_PASTE_LIST -> requestPasteListFocus()
    }
    return true
}
