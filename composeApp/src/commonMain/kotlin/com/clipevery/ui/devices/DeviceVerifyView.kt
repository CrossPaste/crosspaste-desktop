package com.clipevery.ui.devices

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
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.sync.SyncManager
import com.clipevery.ui.base.CustomTextField
import com.clipevery.ui.base.DialogButtonsView
import com.clipevery.ui.base.DialogService
import kotlinx.coroutines.launch

@Composable
fun DeviceVerifyView(syncRuntimeInfo: SyncRuntimeInfo) {
    val current = LocalKoinApplication.current
    val syncManager = current.koin.get<SyncManager>()
    val dialogService = current.koin.get<DialogService>()

    val tokenCount = 6
    val tokens = remember { mutableStateListOf(*Array(tokenCount) { "" }) }
    var isError by remember { mutableStateOf(false) }
    val focusRequesters = List(tokenCount) { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]?.showToken()
    }

    Box(
        Modifier.fillMaxWidth()
            .wrapContentHeight()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight()
                        .padding(10.dp),
            ) {
                DeviceBarView(
                    modifier = Modifier,
                    syncRuntimeInfo,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = syncRuntimeInfo.connectHostAddress ?: "unknown",
                        style =
                            TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        color = MaterialTheme.colors.onBackground,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Spacer(modifier = Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tokens.forEachIndexed { index, token ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .width(40.dp)
                                    .height(50.dp)
                                    .background(MaterialTheme.colors.background, RoundedCornerShape(4.dp))
                                    .border(
                                        1.dp,
                                        if (isError && token.length != 1) {
                                            MaterialTheme.colors.error
                                        } else {
                                            MaterialTheme.colors.primary
                                        },
                                        RoundedCornerShape(4.dp),
                                    ),
                        ) {
                            CustomTextField(
                                value = token,
                                onValueChange = { value ->
                                    if (value.length <= 1 && value.all { it.isDigit() }) {
                                        tokens[index] = value
                                        if (value.isNotEmpty() && index < tokenCount - 1) {
                                            focusRequesters[index + 1].requestFocus()
                                        }
                                    }
                                },
                                isError = isError && (token.length != 1),
                                singleLine = true,
                                textStyle =
                                    LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colors.primary,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                keyboardOptions =
                                    KeyboardOptions.Default.copy(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = if (index == tokenCount - 1) ImeAction.Done else ImeAction.Next,
                                    ),
                                modifier =
                                    Modifier.fillMaxSize()
                                        .focusRequester(focusRequesters[index]),
                                colors =
                                    TextFieldDefaults.textFieldColors(
                                        textColor = MaterialTheme.colors.onBackground,
                                        disabledTextColor = Color.Transparent,
                                        backgroundColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colors.primary,
                                        focusedIndicatorColor = MaterialTheme.colors.primary,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                contentPadding = PaddingValues(4.dp, 10.dp, 4.dp, 10.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            DialogButtonsView(
                height = 50.dp,
                cancelAction = {
                    syncManager.ignoreVerify(syncRuntimeInfo.appInstanceId)
                    dialogService.dialog = null
                },
                confirmAction = {
                    tokens.joinToString("").let { token ->
                        if (token.length == tokenCount) {
                            coroutineScope.launch {
                                syncManager.trustByToken(syncRuntimeInfo.appInstanceId, token.toInt())
                                syncManager.resolveSync(syncRuntimeInfo.appInstanceId, false)
                            }
                            dialogService.dialog = null
                        } else {
                            isError = true
                        }
                    }
                },
            )
        }
    }
}
