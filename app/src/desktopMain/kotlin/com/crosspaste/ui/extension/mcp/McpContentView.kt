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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.crosspaste.utils.GlobalCoroutineScope.ioCoroutineDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.atomic.AtomicLong

@Composable
fun McpContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val mcpServer = koinInject<McpServer>()
    val notificationManager = koinInject<NotificationManager>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()
    val controller =
        remember(configManager, mcpServer, notificationManager) {
            McpServerSettingsController(configManager, mcpServer, notificationManager)
        }
    var counterResetKey by remember { mutableIntStateOf(0) }

    val displayPort =
        if (config.mcpServerPort > 0) {
            config.mcpServerPort
        } else {
            DesktopMcpServer.DEFAULT_PORT
        }

    val mcpCommand = "claude mcp add crosspaste --transport sse http://localhost:$displayPort/"

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
                    controller.setEnabled(enabled, displayPort)
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
                        // Remount the counter only when a failed port change was rolled
                        // back, so normal typing keeps focus while the field still snaps
                        // back to the port the server is actually bound to.
                        key(counterResetKey) {
                            Counter(
                                defaultValue = displayPort.toLong(),
                                rule = { it in 1024..65535 },
                            ) { newPort ->
                                controller.setPort(newPort.toInt()) { counterResetKey++ }
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

/**
 * Applies MCP settings changes to the running server. The config is committed
 * first, the server operation runs on the app-level IO scope so leaving the
 * settings page cannot drop it, and a failed operation rolls the config back to
 * the last working state. Superseded requests are dropped by version.
 */
private class McpServerSettingsController(
    private val configManager: DesktopConfigManager,
    private val mcpServer: McpServer,
    private val notificationManager: NotificationManager,
) {

    private val operationVersion = AtomicLong()

    fun setEnabled(
        enabled: Boolean,
        port: Int,
    ) {
        val requestVersion = operationVersion.incrementAndGet()
        val previousEnabled = configManager.getCurrentConfig().enableMcpServer
        configManager.updateConfig("enableMcpServer", enabled)
        if (configManager.getCurrentConfig().enableMcpServer != enabled) {
            return
        }
        ioCoroutineDispatcher.launch {
            if (!isCurrent(requestVersion) || configManager.getCurrentConfig().enableMcpServer != enabled) {
                return@launch
            }
            val succeeded =
                runServerOperation {
                    if (enabled) {
                        mcpServer.restart(port)
                    } else {
                        mcpServer.stop()
                    }
                }
            if (!succeeded &&
                isCurrent(requestVersion) &&
                configManager.getCurrentConfig().enableMcpServer == enabled
            ) {
                configManager.updateConfig("enableMcpServer", previousEnabled)
            }
        }
    }

    fun setPort(
        requestedPort: Int,
        onRolledBack: () -> Unit,
    ) {
        val requestVersion = operationVersion.incrementAndGet()
        configManager.updateConfig("mcpServerPort", requestedPort)
        if (!portRequestStillApplies(requestedPort)) {
            return
        }
        ioCoroutineDispatcher.launch {
            if (!isCurrent(requestVersion) || !portRequestStillApplies(requestedPort)) {
                return@launch
            }
            val succeeded = runServerOperation { mcpServer.restart(requestedPort) }
            if (succeeded || !isCurrent(requestVersion) || !portRequestStillApplies(requestedPort)) {
                return@launch
            }
            val actualPort = mcpServer.port()
            withContext(mainDispatcher) {
                if (isCurrent(requestVersion) && configManager.getCurrentConfig().mcpServerPort == requestedPort) {
                    configManager.updateConfig("mcpServerPort", actualPort)
                    onRolledBack()
                }
            }
        }
    }

    private fun isCurrent(requestVersion: Long): Boolean = requestVersion == operationVersion.get()

    private fun portRequestStillApplies(requestedPort: Int): Boolean {
        val config = configManager.getCurrentConfig()
        return config.enableMcpServer && config.mcpServerPort == requestedPort
    }

    private suspend fun runServerOperation(operation: suspend () -> Unit): Boolean =
        try {
            operation()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            notificationManager.sendNotification(
                title = { it.getText("mcp_server_update_failed") },
                messageType = MessageType.Error,
            )
            false
        }
}
