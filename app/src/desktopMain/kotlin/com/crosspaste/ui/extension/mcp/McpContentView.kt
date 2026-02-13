package com.crosspaste.ui.extension.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Code
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.mcp.DesktopMcpServer
import com.crosspaste.mcp.McpServer
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingListSwitchItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun McpContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val mcpServer = koinInject<McpServer>()
    val notificationManager = koinInject<NotificationManager>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()
    val scope = rememberCoroutineScope()

    val displayPort =
        if (config.mcpServerPort > 0) {
            config.mcpServerPort
        } else {
            DesktopMcpServer.DEFAULT_PORT
        }

    val mcpCommand = "claude mcp add crosspaste --transport sse http://localhost:$displayPort/sse"

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "mcp_server",
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Code,
                            iconColor = themeExt.indigoIconColor,
                        ),
                    checked = config.enableMcpServer,
                ) { enabled ->
                    configManager.updateConfig("enableMcpServer", enabled)
                    scope.launch {
                        if (enabled) {
                            mcpServer.restart(displayPort)
                        } else {
                            mcpServer.stop()
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "mcp_server_port",
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Code,
                            iconColor = themeExt.indigoIconColor,
                        ),
                    trailingContent = {
                        Counter(
                            defaultValue = displayPort.toLong(),
                            rule = { it in 1024..65535 },
                        ) { newPort ->
                            configManager.updateConfig("mcpServerPort", newPort.toInt())
                            if (config.enableMcpServer) {
                                scope.launch {
                                    mcpServer.restart(newPort.toInt())
                                }
                            }
                        }
                    },
                )
            }
        }

        item {
            SectionHeader("mcp_claude_config_hint", topPadding = medium)
        }

        item {
            SettingSectionCard {
                Column(
                    modifier = Modifier.padding(medium),
                ) {
                    SelectionContainer {
                        Text(
                            text = mcpCommand,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(mcpCommand), null)
                            notificationManager.sendNotification(
                                title = { it.getText("mcp_config_copied") },
                                messageType = MessageType.Success,
                            )
                        },
                        modifier = Modifier.padding(top = tiny),
                    ) {
                        Text(copywriter.getText("mcp_copy_config"))
                    }
                }
            }
        }
    }
}
