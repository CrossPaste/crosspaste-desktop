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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DeviceVerifyView(syncRuntimeInfo: SyncRuntimeInfo) {
    val syncManager = koinInject<SyncManager>()
    val dialogService = koinInject<DialogService>()

    val tokenCount = 6
    val tokens = remember { mutableStateListOf(*Array(tokenCount) { "" }) }
    var isError by remember { mutableStateOf(false) }
    val focusRequesters = List(tokenCount) { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]?.showToken()
        focusRequesters.firstOrNull()?.requestFocus()
    }

    val setError = { value: Boolean -> isError = value }

    val confirmAction = { confirmToken(tokens, tokenCount, setError, syncManager, syncRuntimeInfo, dialogService, coroutineScope) }
    val cancelAction = { cancelVerification(syncManager, syncRuntimeInfo, dialogService) }

    Box(
        Modifier.fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            VerificationContent(syncRuntimeInfo, tokens, isError, focusRequesters, confirmAction, cancelAction)
            Spacer(modifier = Modifier.height(10.dp))
            DialogButtonsView(
                height = 50.dp,
                cancelAction = cancelAction,
                confirmAction = confirmAction,
            )
        }
    }
}

@Composable
fun VerificationContent(
    syncRuntimeInfo: SyncRuntimeInfo,
    tokens: MutableList<String>,
    isError: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight()
                .padding(10.dp),
    ) {
        DeviceInfoHeader(syncRuntimeInfo)
        Spacer(modifier = Modifier.size(8.dp))
        TokenInputRow(tokens, isError, focusRequesters, confirmAction, cancelAction)
    }
}

@Composable
fun DeviceInfoHeader(syncRuntimeInfo: SyncRuntimeInfo) {
    DeviceBarView(
        modifier = Modifier,
        syncRuntimeInfo,
    ) {
        Row(horizontalArrangement = Arrangement.End) {
            Text(
                text = syncRuntimeInfo.connectHostAddress ?: "unknown",
                style =
                    TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
fun TokenInputRow(
    tokens: MutableList<String>,
    isError: Boolean,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                .width(40.dp)
                .height(50.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                .border(
                    1.dp,
                    if (isError && token.length != 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(4.dp),
                ),
    ) {
        CustomTextField(
            value = token,
            onValueChange = onValueChange,
            isError = isError && (token.length != 1),
            singleLine = true,
            textStyle =
                LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == focusRequesters.size - 1) ImeAction.Done else ImeAction.Next,
                ),
            modifier =
                Modifier.fillMaxSize()
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { handleKeyEvent(it, token, index, focusRequesters, confirmAction, cancelAction) },
            colors = textFieldColors(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
        )
    }
}

fun handleKeyEvent(
    event: KeyEvent,
    token: String,
    index: Int,
    focusRequesters: List<FocusRequester>,
    confirmAction: () -> Unit,
    cancelAction: () -> Unit,
): Boolean {
    return when (event.key) {
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
    coroutineScope: CoroutineScope,
) {
    tokens.joinToString("").let { token ->
        if (token.length == tokenCount) {
            coroutineScope.launch {
                syncManager.trustByToken(syncRuntimeInfo.appInstanceId, token.toInt())
            }
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
