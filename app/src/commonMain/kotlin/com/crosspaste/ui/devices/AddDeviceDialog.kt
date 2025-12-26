package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.NetUtils
import com.crosspaste.utils.buildUrl
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@Composable
fun AddDeviceDialog(onDismiss: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val syncClientApi = koinInject<SyncClientApi>()
    val syncManager = koinInject<SyncManager>()

    val appSizeValue = LocalAppSizeValueState.current

    var ip by remember { mutableStateOf("") }

    var port by remember { mutableStateOf("13129") }

    // Determine if the confirm button should be enabled
    val isInputValid =
        remember(ip, port) {
            NetUtils.isValidIp(ip) && NetUtils.isValidPort(port)
        }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("add_device_manually"),
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
                    text = copywriter.getText("add_device_manually_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )

                Column(verticalArrangement = Arrangement.spacedBy(medium)) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { newValue ->
                            // Only update if it matches basic IP character patterns
                            val formatted = NetUtils.formatIpInput(newValue)
                            if (formatted.length <= 15) {
                                ip = formatted
                            }
                        },
                        label = { Text("IP") },
                        placeholder = { Text("192.168.0.10") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } &&
                                (newValue.isEmpty() || newValue.toIntOrNull() in 0..65535)
                            ) {
                                port = newValue
                            }
                        },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            // Remove .fillMaxHeight() here
                            Column(
                                modifier = Modifier.offset(8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                IconButton(
                                    shape = RectangleShape,
                                    onClick = {
                                        val current = port.toIntOrNull() ?: 0
                                        if (current < 65535) port = (current + 1).toString()
                                    },
                                    modifier = Modifier.size(24.dp), // Constrain the size of buttons
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Increase",
                                        modifier = Modifier.size(20.dp), // Make the icon itself smaller
                                    )
                                }

                                IconButton(
                                    shape = RectangleShape,
                                    onClick = {
                                        val current = port.toIntOrNull() ?: 0
                                        if (current > 0) port = (current - 1).toString()
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Decrease",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Logic inside click stays simple because button is only enabled when valid
                    runBlocking {
                        val hostAndPort = HostAndPort(ip, port.toInt())
                        val result = syncClientApi.syncInfo { buildUrl(hostAndPort) }

                        if (result is SuccessResult) {
                            val syncInfo = result.getResult<SyncInfo>()
                            syncManager.updateSyncInfo(syncInfo)
                            onDismiss() // Close dialog after success
                        } else {
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
                },
                enabled = isInputValid,
            ) {
                Text(copywriter.getText("confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(copywriter.getText("cancel"))
            }
        },
    )
}
