package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import com.crosspaste.platform.Platform
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.side.SidePasteboardContentView
import com.crosspaste.ui.theme.AppUIColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SideSearchWindowContent() {
    val viewModel = koinInject<PasteSelectionViewModel>()
    val platform = koinInject<Platform>()

    val scope = rememberCoroutineScope()

    val backgroundModifier =
        if (!platform.isMacos()) {
            Modifier.background(AppUIColors.generalBackground)
        } else {
            Modifier
        }

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
    }
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
