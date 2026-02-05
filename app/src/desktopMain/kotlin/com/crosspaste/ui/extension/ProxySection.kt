package com.crosspaste.ui.extension

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.AnimatedSegmentedControl
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.PortTextField
import com.crosspaste.ui.extension.ProxyType.HTTP
import com.crosspaste.ui.extension.ProxyType.SOCKS
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import org.koin.compose.koinInject

object ProxyType {
    const val HTTP = "HTTP"
    const val SOCKS = "SOCKS"
}

@Composable
fun ProxySection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val config by configManager.config.collectAsState()
    val themeExt = LocalThemeExtState.current

    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    var useManualProxy by remember { mutableStateOf(config.useManualProxy) }

    var protocol by remember { mutableStateOf(config.proxyType) }

    var hostname by remember { mutableStateOf(config.proxyHost) }

    var port by remember { mutableStateOf(config.proxyPort) }

    SettingSectionCard {
        SettingListItem(
            title = "proxy",
            subtitle = "proxy_extension_network_requests",
            icon = IconData(Icons.Default.DeviceHub, themeExt.cyanIconColor),
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onToggle,
        )

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(start = medium, end = medium, bottom = medium),
                verticalArrangement = Arrangement.spacedBy(small2X),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(tiny3X),
                ) {
                    ProxyRadioButton(copywriter.getText("no_proxy"), !useManualProxy) {
                        useManualProxy = false
                    }
                    ProxyRadioButton(copywriter.getText("manual_proxy_configuration"), useManualProxy) {
                        useManualProxy = true
                    }
                }

                if (useManualProxy) {
                    ManualConfigContent(
                        protocol = protocol,
                        onProtocolChange = { protocol = it },
                        hostname = hostname,
                        onHostnameChange = {
                            hostname = it
                        },
                        port = port,
                        onPortChange = {
                            port = it
                        },
                    ) {
                        configManager.updateConfig(
                            listOf("useManualProxy", "proxyType", "proxyHost", "proxyPort"),
                            listOf(
                                useManualProxy,
                                protocol,
                                hostname,
                                port,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualConfigContent(
    protocol: String,
    onProtocolChange: (String) -> Unit,
    hostname: String,
    onHostnameChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    save: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Column(
        modifier = Modifier.padding(start = tiny),
        verticalArrangement = Arrangement.spacedBy(medium),
    ) {
        AnimatedSegmentedControl(
            items = listOf(HTTP, SOCKS),
            selectedItem = protocol,
            onItemSelected = { onProtocolChange(it) },
        )

        // Host and Port Fields
        Row(horizontalArrangement = Arrangement.spacedBy(tiny)) {
            OutlinedTextField(
                value = hostname,
                onValueChange = onHostnameChange,
                label = { Text("Host") },
                placeholder = { Text("127.0.0.1") },
                modifier = Modifier.weight(2f),
                shape = small2XRoundedCornerShape,
            )
            PortTextField(
                value = port,
                onValueChange = onPortChange,
                label = "Port",
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = save,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            shape = RoundedCornerShape(100.dp),
        ) {
            Text(copywriter.getText("save_proxy_settings"))
        }
    }
}

@Composable
private fun ProxyRadioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(small2XRoundedCornerShape)
                .clickable { onClick() }
                .padding(tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(small2X))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
