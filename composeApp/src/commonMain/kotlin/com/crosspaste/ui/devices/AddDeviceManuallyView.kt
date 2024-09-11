package com.crosspaste.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.sync.createSyncRuntimeInfo
import com.crosspaste.ui.base.DefaultTextField
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.connectedColor
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
    val syncRuntimeInfoRealm = koinInject<SyncRuntimeInfoRealm>()

    Row(
        modifier =
            Modifier.wrapContentWidth()
                .height(40.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        var ip by remember { mutableStateOf("") }
        var ipIsError by remember { mutableStateOf(false) }

        var port by remember { mutableStateOf("") }
        var portIsError by remember { mutableStateOf(false) }

        Text(
            text = "IP:",
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.width(5.dp))

        DefaultTextField(
            fixContentWidth = 137.5.dp,
            isError = ipIsError,
            value = ip,
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
                        try {
                            it.toInt().toString()
                        } catch (e: NumberFormatException) {
                            ""
                        }
                    }
                ip = newIp
            }
        }

        Spacer(modifier = Modifier.width(15.dp))

        Text(
            text = "Port",
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.width(5.dp))

        DefaultTextField(
            fixContentWidth = 46.dp,
            isError = portIsError,
            value = port,
        ) { newValue ->
            try {
                val intPort = newValue.toInt()
                if (intPort > 0) {
                    port = intPort.toString()
                }
            } catch (e: NumberFormatException) {
                return@DefaultTextField
            }
        }

        Spacer(modifier = Modifier.width(15.dp))

        Button(
            modifier = Modifier.height(28.dp),
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
                            syncClientApi.syncInfo { urlBuilder ->
                                buildUrl(urlBuilder, ip, port.toInt())
                            }
                    ) {
                        is SuccessResult -> {
                            // add device
                            val syncInfo = result.getResult<SyncInfo>()
                            val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
                            syncRuntimeInfoRealm.insertOrUpdate(newSyncRuntimeInfo)
                            ip = ""
                            port = ""
                        }
                        else -> {
                            notificationManager.addNotification(
                                message =
                                    "${copywriter.getText("addition_failed")}\n" +
                                        "1. ${copywriter.getText("please_check_if_the_ip_and_port_are_correct")}\n" +
                                        "2. ${copywriter.getText(
                                            "check_if_there_is_a_firewall_" +
                                                "or_antivirus_software_blocking_the_connection",
                                        )}",
                                messageType = MessageType.Error,
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, connectedColor()),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
            elevation =
                ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                text = copywriter.getText("add"),
                color = connectedColor(),
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                    ),
            )
        }
    }
}
