package com.crosspaste.ui.base

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition

class PasteContextMenuRepresentation : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(
        state: ContextMenuState,
        items: () -> List<ContextMenuItem>,
    ) {
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            var focusManager: FocusManager? by mutableStateOf(null)
            var inputModeManager: InputModeManager? by mutableStateOf(null)

            Popup(
                onDismissRequest = { state.status = ContextMenuState.Status.Closed },
                popupPositionProvider =
                    rememberPopupPositionProviderAtPosition(
                        positionPx = status.rect.center,
                    ),
                properties =
                    PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
                onKeyEvent = {
                    if (it.type == KeyEventType.KeyDown) {
                        when (it.key.nativeKeyCode) {
                            java.awt.event.KeyEvent.VK_DOWN -> {
                                inputModeManager!!.requestInputMode(InputMode.Keyboard)
                                focusManager!!.moveFocus(FocusDirection.Next)
                                true
                            }
                            java.awt.event.KeyEvent.VK_UP -> {
                                inputModeManager!!.requestInputMode(InputMode.Keyboard)
                                focusManager!!.moveFocus(FocusDirection.Previous)
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            ) {
                focusManager = LocalFocusManager.current
                inputModeManager = LocalInputModeManager.current
                Box(
                    modifier =
                        Modifier.wrapContentSize()
                            .background(Color.Transparent)
                            .shadow(15.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .width(IntrinsicSize.Max)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surface),
                    ) {
                        items().forEach { item ->
                            MenuItem(
                                text = item.label,
                            ) {
                                state.status = ContextMenuState.Status.Closed
                                item.onClick()
                            }
                        }
                    }
                }
            }
        }
    }
}
