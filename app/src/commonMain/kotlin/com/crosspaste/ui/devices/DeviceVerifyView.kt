package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.generalBodyTextStyle
import com.crosspaste.ui.theme.AppUIFont.tokenTextStyle
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun DeviceScope.DeviceVerifyView() {
    val syncManager = koinInject<SyncManager>()
    val dialogService = koinInject<DialogService>()

    val tokenCount = 6
    val tokens = remember { mutableStateListOf(*Array(tokenCount) { "" }) }
    var isError by remember { mutableStateOf(false) }
    val focusRequesters = remember { List(tokenCount) { FocusRequester() } }

    LaunchedEffect(Unit) {
        syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]?.showToken()
    }

    val setError = { value: Boolean -> isError = value }

    val confirmAction = {
        confirmToken(
            tokens = tokens,
            tokenCount = tokenCount,
            setError = setError,
            syncManager = syncManager,
            syncRuntimeInfo = syncRuntimeInfo,
            dialogService = dialogService,
        )
    }
    val cancelAction = {
        cancelVerification(syncManager, syncRuntimeInfo, dialogService)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(AppUIColors.generalBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            VerificationContent(
                tokens,
                isError,
                focusRequesters,
                confirmAction,
                cancelAction,
            )
            DialogButtonsView(
                cancelAction = cancelAction,
                confirmAction = confirmAction,
            )
        }
    }
}

@Composable
fun DeviceScope.VerificationContent(
    tokens: MutableList<String>,
    isError: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
    ) {
        DeviceTokenHeader()
        Spacer(modifier = Modifier.size(large2X))
        TokenInputRow(tokens, isError, focusRequesters, confirmAction, cancelAction)
    }
}

@Composable
fun DeviceScope.DeviceTokenHeader() {
    DeviceRowContent(
        style = tokenDeviceStyle,
        trailingContent = {
            Row(horizontalArrangement = Arrangement.End) {
                Text(
                    text = syncRuntimeInfo.connectHostAddress ?: "unknown",
                    style = generalBodyTextStyle,
                )
                Spacer(modifier = Modifier.width(small2X))
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
                .wrapContentHeight()
                .padding(horizontal = small2X),
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
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .width(xxxLarge)
                .height(xxxxLarge)
                .background(MaterialTheme.colorScheme.background, tiny3XRoundedCornerShape)
                .border(
                    tiny5X,
                    if (isError && token.length != 1) {
                        AppUIColors.errorColor
                    } else {
                        AppUIColors.importantColor
                    },
                    tiny3XRoundedCornerShape,
                ),
    ) {
        CustomTextField(
            value = token,
            onValueChange = onValueChange,
            isError = isError && (token.length != 1),
            singleLine = true,
            textStyle = tokenTextStyle,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == focusRequesters.size - 1) ImeAction.Done else ImeAction.Next,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { handleKeyEvent(it, token, index, focusRequesters, confirmAction, cancelAction) },
            colors = textFieldColors(),
            contentPadding = PaddingValues(horizontal = tiny3X, vertical = small3X),
        )

        if (index == 0) {
            LaunchedEffect(focusRequesters[index]) {
                kotlinx.coroutines.delay(10)
                focusRequesters[index].requestFocus()
            }
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

@Composable
fun textFieldColors() =
    TextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        disabledTextColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

fun confirmToken(
    tokens: MutableList<String>,
    tokenCount: Int,
    setError: (Boolean) -> Unit,
    syncManager: SyncManager,
    syncRuntimeInfo: SyncRuntimeInfo,
    dialogService: DialogService,
) {
    tokens.joinToString("").let { token ->
        if (token.length == tokenCount) {
            syncManager.trustByToken(syncRuntimeInfo.appInstanceId, token.toInt())
            dialogService.popDialog()
        } else {
            setError(true)
        }
    }
}

fun cancelVerification(
    syncManager: SyncManager,
    syncRuntimeInfo: SyncRuntimeInfo,
    dialogService: DialogService,
) {
    syncManager.ignoreVerify(syncRuntimeInfo.appInstanceId)
    dialogService.popDialog()
}
