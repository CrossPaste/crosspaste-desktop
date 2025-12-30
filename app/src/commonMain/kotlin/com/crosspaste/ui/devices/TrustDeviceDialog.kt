package com.crosspaste.ui.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogProperties
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUIFont.generalBodyTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun DeviceScope.TrustDeviceDialog() {
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()

    val appSizeValue = LocalAppSizeValueState.current

    val tokenCount = 6
    val tokens = remember { mutableStateListOf(*Array(tokenCount) { "" }) }
    var isError by remember { mutableStateOf(false) }
    val focusRequesters = remember { List(tokenCount) { FocusRequester() } }

    val setError = { value: Boolean -> isError = value }

    val confirmAction = {
        confirmToken(
            tokens = tokens,
            tokenCount = tokenCount,
            setError = setError,
            syncManager = syncManager,
            syncRuntimeInfo = syncRuntimeInfo,
        )
    }

    val cancelAction = {
        cancelVerification(syncManager, syncRuntimeInfo)
    }

    LaunchedEffect(Unit) {
        syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]?.showToken()
    }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = cancelAction,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("do_you_trust_this_device?"),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = tiny),
                verticalArrangement = Arrangement.spacedBy(xLarge),
            ) {
                Text(
                    text = copywriter.getText("trust_this_device_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )
                DeviceRowContent(
                    style = tokenDeviceStyle,
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tiny),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = syncRuntimeInfo.connectHostAddress ?: "unknown",
                                style = generalBodyTextStyle,
                            )
                        }
                    },
                )
                TokenInputRow(tokens, isError, focusRequesters, confirmAction, cancelAction)
            }
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText("confirm"),
                type = DialogButtonType.FILLED,
            ) {
                confirmAction()
            }
        },
        dismissButton = {
            TextButton(onClick = cancelAction) {
                Text(copywriter.getText("cancel"))
            }
        },
    )
}

@Composable
fun TokenInputRow(
    tokens: MutableList<String>,
    isError: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tokens.forEachIndexed { index, token ->
            TokenInputBox(
                token = token,
                index = index,
                isError = isError,
                focusRequesters = focusRequesters,
                onValueChange = { value ->
                    if (value.length <= 1 && value.all { it.isDigit() }) {
                        tokens[index] = value
                        if (value.isNotEmpty() && index < tokens.size - 1) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                confirmAction = confirmAction,
                cancelAction = cancelAction,
            )
        }
    }
}

@Composable
fun TokenInputBox(
    token: String,
    index: Int,
    isError: Boolean,
    focusRequesters: List<FocusRequester>,
    onValueChange: (String) -> Unit,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    var isFocused by remember { mutableStateOf(false) }

    val focusRequester = focusRequesters[index]

    val borderColor: Color =
        when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }

    val borderWidth = if (isFocused || isError) tiny4X else tiny5X

    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val textColor =
        if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    val mergedTextStyle =
        MaterialTheme.typography.headlineMedium.copy(
            color = textColor,
            textAlign = TextAlign.Center,
            lineHeight = 1.em,
            fontFamily = FontFamily.Monospace,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
        )

    Surface(
        modifier =
            modifier
                .width(xxxLarge)
                .height(xxxxLarge)
                .onFocusChanged { isFocused = it.isFocused }
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = shape,
                ).onKeyEvent { handleKeyEvent(it, token, index, focusRequesters, confirmAction, cancelAction) },
        shape = shape,
        color = containerColor,
        tonalElevation = if (isFocused) tiny4X else zero,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            BasicTextField(
                value = token,
                onValueChange = {
                    if (it.length <= 1) {
                        onValueChange(it)
                    }
                },
                modifier =
                    Modifier
                        .focusRequester(focusRequester)
                        .wrapContentSize(),
                textStyle = mergedTextStyle,
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == focusRequesters.size - 1) ImeAction.Done else ImeAction.Next,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.defaultMinSize(minWidth = xxxLarge),
                    ) {
                        innerTextField()
                    }
                },
            )
        }
    }

    if (index == 0) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

fun handleKeyEvent(
    event: KeyEvent,
    token: String,
    index: Int,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
): Boolean =
    when (event.key) {
        Key.Enter -> {
            confirmAction()
            true
        }
        Key.Escape -> {
            cancelAction()
            true
        }
        Key.Backspace -> {
            if (token.isEmpty() && index > 0) {
                focusRequesters[index - 1].requestFocus()
                true
            } else {
                false
            }
        }
        else -> false
    }

fun confirmToken(
    tokens: MutableList<String>,
    tokenCount: Int,
    setError: (Boolean) -> Unit,
    syncManager: SyncManager,
    syncRuntimeInfo: SyncRuntimeInfo,
) {
    tokens.joinToString("").let { token ->
        if (token.length == tokenCount) {
            syncManager.trustByToken(syncRuntimeInfo.appInstanceId, token.toInt())
        } else {
            setError(true)
        }
    }
}

fun cancelVerification(
    syncManager: SyncManager,
    syncRuntimeInfo: SyncRuntimeInfo,
) {
    syncManager.ignoreVerify(syncRuntimeInfo.appInstanceId)
}
