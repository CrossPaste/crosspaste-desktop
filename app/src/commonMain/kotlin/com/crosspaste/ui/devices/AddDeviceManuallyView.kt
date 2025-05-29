package com.crosspaste.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.createSyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.DefaultTextField
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.base.textFieldStyle
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.ui.theme.CrossPasteTheme.connectedColor
import com.crosspaste.utils.buildUrl
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@Composable
fun AddDeviceManuallyView() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AddDeviceManuallyForm()
    }
}

@Composable
fun AddDeviceManuallyForm() {
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val syncClientApi = koinInject<SyncClientApi>()
    val syncRuntimeInfoDao = koinInject<SyncRuntimeInfoDao>()

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(xxxxLarge)
                .background(AppUIColors.deviceBackground)
                .padding(horizontal = small2X, vertical = tiny2X),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var ip by remember { mutableStateOf("") }
        var ipIsError by remember { mutableStateOf(false) }

        var port by remember { mutableStateOf("") }
        var portIsError by remember { mutableStateOf(false) }

        Text(
            text = "IP",
            color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.deviceBackground),
            style = MaterialTheme.typography.labelLarge,
        )

        Spacer(modifier = Modifier.width(tiny))

        val ipWidth =
            measureTextWidth(
                "000.000.000.000",
                textFieldStyle(),
            )

        DefaultTextField(
            modifier =
                Modifier
                    .weight(0.9f)
                    .widthIn(max = ipWidth + medium),
            isError = ipIsError,
            textAlign = TextAlign.Center,
            value = ip,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            contentPadding = PaddingValues(horizontal = tiny),
        ) { newValue ->
            val filteredValue = newValue.filter { it.isDigit() || it == '.' }
            if (filteredValue.length <= 15 && filteredValue == newValue) {
                val ipArray = filteredValue.split(".")
                ipArray.forEach {
                    if (it.isNotEmpty() && it.toInt() > 255) {
                        return@DefaultTextField
                    }
                }
                // remove leading zeros
                val newIp =
                    ipArray.joinToString(".") {
                        runCatching {
                            it.toInt().toString()
                        }.getOrElse { "" }
                    }
                ip = newIp
            }
        }

        Spacer(modifier = Modifier.width(medium))

        Text(
            text = "Port",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )

        Spacer(modifier = Modifier.width(tiny))

        val portWidth =
            measureTextWidth(
                "10000",
                textFieldStyle(),
            )

        DefaultTextField(
            modifier =
                Modifier
                    .weight(0.5f)
                    .widthIn(max = portWidth + medium),
            isError = portIsError,
            textAlign = TextAlign.Center,
            value = port,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            contentPadding = PaddingValues(horizontal = tiny),
        ) { newValue ->
            runCatching {
                if (newValue.isEmpty()) {
                    port = ""
                } else {
                    val intPort = newValue.toInt()
                    if (intPort > 0) {
                        port = intPort.toString()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(medium))

        Button(
            modifier =
                Modifier.wrapContentWidth()
                    .height(xxLarge)
                    .weight(0.4f),
            onClick = {
                // check ip and port
                if (ip.isEmpty()) {
                    ipIsError = true
                    return@Button
                } else {
                    ipIsError = false
                }

                if (port.isEmpty()) {
                    portIsError = true
                    return@Button
                } else {
                    portIsError = false
                }

                runBlocking {
                    when (
                        val result =
                            syncClientApi.syncInfo {
                                buildUrl(ip, port.toInt())
                            }
                    ) {
                        is SuccessResult -> {
                            // add device
                            val syncInfo = result.getResult<SyncInfo>()
                            val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
                            syncRuntimeInfoDao.insertOrUpdateSyncRuntimeInfo(newSyncRuntimeInfo)
                            ip = ""
                            port = ""
                        }
                        else -> {
                            notificationManager.sendNotification(
                                title = { it.getText("addition_failed") },
                                message = {
                                    "1. ${it.getText("please_check_if_the_ip_and_port_are_correct")}\n" +
                                        "2. ${it.getText(
                                            "check_if_there_is_a_firewall_or_antivirus_software_blocking_the_connection",
                                        )}"
                                },
                                messageType = MessageType.Error,
                            )
                        }
                    }
                }
            },
            shape = tiny3XRoundedCornerShape,
            border = BorderStroke(tiny5X, connectedColor(MaterialTheme.colorScheme.surfaceContainerLowest)),
            contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            elevation =
                ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = zero,
                    pressedElevation = zero,
                    hoveredElevation = zero,
                    focusedElevation = zero,
                ),
        ) {
            Text(
                text = copywriter.getText("add"),
                color = connectedColor(MaterialTheme.colorScheme.surfaceContainerLowest),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
