package com.crosspaste.ui.extension

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Terminal
import com.crosspaste.cli.CliInstallResult
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.cli.CliSymlinkState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.settings.SettingListItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extension-page row for the macOS `crosspaste` terminal command: shows whether the
 * /usr/local/bin symlink is in place and offers install/repair. Only rendered
 * when [CliSymlinkService] reports the feature as supported — the caller
 * gates on [CliSymlinkState.NOT_SUPPORTED].
 */
@Composable
fun CliToolSettingItem() {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val themeExt = LocalThemeExtState.current

    val scope = rememberCoroutineScope()
    val state by cliSymlinkService.state.collectAsState()
    var installing by remember { mutableStateOf(false) }

    SettingListItem(
        title = "install_cli_tool",
        subtitle =
            when (state) {
                CliSymlinkState.TRANSLOCATED -> "cli_tool_translocated_desc"
                CliSymlinkState.CONFLICT -> "cli_tool_conflict_desc"
                else -> "install_cli_tool_desc"
            },
        icon = IconData(MaterialSymbols.Rounded.Terminal, themeExt.greenIconColor),
        trailingContent = {
            when (state) {
                CliSymlinkState.INSTALLED -> {
                    Text(
                        text = copywriter.getText("cli_tool_installed"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CliSymlinkState.NOT_INSTALLED, CliSymlinkState.NEEDS_REPAIR -> {
                    val actionKey = if (state == CliSymlinkState.NEEDS_REPAIR) "repair" else "install"
                    TextButton(
                        enabled = !installing,
                        onClick = {
                            installing = true
                            scope.launch {
                                when (cliSymlinkService.install()) {
                                    CliInstallResult.SUCCESS -> {
                                        notificationManager.sendNotification(
                                            title = { it.getText("cli_tool_installed") },
                                            message = { CliSymlinkService.DEFAULT_LINK_PATH },
                                            messageType = MessageType.Success,
                                        )
                                    }
                                    CliInstallResult.CANCELLED -> Unit
                                    CliInstallResult.FAILURE -> {
                                        notificationManager.sendNotification(
                                            title = { it.getText("install_cli_tool_failed") },
                                            messageType = MessageType.Error,
                                        )
                                    }
                                }
                                installing = false
                            }
                        },
                    ) {
                        Text(copywriter.getText(if (installing) "installing" else actionKey))
                    }
                }
                // Explained via the state-dependent subtitle; deliberately no
                // action button — installing would create a doomed or
                // destructive link
                CliSymlinkState.TRANSLOCATED, CliSymlinkState.CONFLICT -> Unit
                CliSymlinkState.NOT_SUPPORTED -> Unit
            }
        },
    )
}
