package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.theme.AppUIFont.noteNameTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.zero

@Composable
fun DeviceNoteEditField(
    cancelAction: () -> Unit,
    confirmAction: () -> Unit,
    inputNoteName: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    syncRuntimeInfo: SyncRuntimeInfo,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    CustomTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(xxxLarge)
                .focusRequester(focusRequester)
                .onKeyEvent {
                    when (it.key) {
                        Key.Enter -> {
                            confirmAction()
                            true
                        }
                        Key.Escape -> {
                            cancelAction()
                            true
                        }
                        else -> {
                            false
                        }
                    }
                },
        value = inputNoteName,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                modifier = Modifier.wrapContentSize(),
                text = syncRuntimeInfo.noteName ?: syncRuntimeInfo.deviceName,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        isError = isError,
        singleLine = true,
        textStyle = noteNameTextStyle,
        contentPadding = PaddingValues(horizontal = medium, vertical = zero),
    )
}
