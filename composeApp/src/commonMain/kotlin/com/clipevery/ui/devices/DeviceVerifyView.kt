package com.clipevery.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.sync.SyncManager
import com.clipevery.ui.base.CustomTextField
import kotlinx.coroutines.launch

@Composable
fun DeviceVerifyView(syncRuntimeInfo: SyncRuntimeInfo) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val syncManager = current.koin.get<SyncManager>()

    val tokenCount = 6
    val tokens = remember { mutableStateListOf(*Array(tokenCount) { "" }) }
    var isError by remember { mutableStateOf(false) }
    val focusRequesters = List(tokenCount) { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]?.showToken()
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.White.copy(alpha = 0.2f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.shadow(5.dp, RoundedCornerShape(5.dp))) {
            Column(
                modifier =
                    Modifier.width(320.dp)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.surface),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .wrapContentHeight()
                            .padding(horizontal = 10.dp)
                            .padding(top = 10.dp, bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = copywriter.getText("Do_you_trust_this_device?"),
                            style = MaterialTheme.typography.subtitle1,
                            color = MaterialTheme.colors.onBackground,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            modifier = Modifier.padding(12.dp).size(36.dp),
                            painter = PlatformPainter(syncRuntimeInfo),
                            contentDescription = "OS Icon",
                            tint = MaterialTheme.colors.onBackground,
                        )
                    }

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
                                        .height(60.dp)
                                        .background(MaterialTheme.colors.background, RoundedCornerShape(4.dp))
                                        .border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(4.dp)),
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
                                    isError = token.length <= 1,
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
                                            errorCursorColor = Color.Red,
                                            focusedIndicatorColor = MaterialTheme.colors.primary,
                                            unfocusedIndicatorColor = MaterialTheme.colors.secondaryVariant,
                                            disabledIndicatorColor = Color.Transparent,
                                        ),
                                    contentPadding = PaddingValues(4.dp, 16.dp, 4.dp, 16.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Divider(modifier = Modifier.fillMaxWidth().width(1.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier =
                                Modifier.weight(0.5f)
                                    .height(60.dp)
                                    .clickable {
                                        syncManager.ignoreVerify(syncRuntimeInfo.appInstanceId)
                                    },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = copywriter.getText("No"),
                                color = MaterialTheme.colors.primary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
                        Row(
                            modifier =
                                Modifier.weight(0.5f)
                                    .height(60.dp)
                                    .clickable {
                                        tokens.joinToString("").let { token ->
                                            if (token.length == tokenCount) {
                                                coroutineScope.launch {
                                                    syncManager.trustByToken(syncRuntimeInfo.appInstanceId, token.toInt())
                                                    syncManager.resolveSync(syncRuntimeInfo.appInstanceId, false)
                                                }
                                            } else {
                                                isError = true
                                            }
                                        }
                                    },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = copywriter.getText("Yes"),
                                color = MaterialTheme.colors.primary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
