package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Terminal
import com.crosspaste.cli.CliInstallResult
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * One-time macOS prompt offering to install the `crosspaste` terminal command
 * (a /usr/local/bin symlink to the bundled CLI). [doneAction] runs when the
 * prompt has served its purpose — installed or dismissed — so the caller can
 * clear the config flag; the settings entry remains for later installs.
 */
@Composable
fun InstallCliDialog(doneAction: () -> Unit) {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()

    val appSizeValue = LocalAppSizeValueState.current
    val scope = rememberCoroutineScope()

    var installing by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {},
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("install_cli_tool"),
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
                    text = copywriter.getText("install_cli_tool_prompt"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText(if (installing) "installing" else "install"),
                type = DialogButtonType.FILLED,
            ) {
                if (installing) return@DialogActionButton
                installing = true
                scope.launch {
                    when (cliSymlinkService.install()) {
                        CliInstallResult.SUCCESS -> {
                            notificationManager.sendNotification(
                                title = { it.getText("cli_tool_installed") },
                                message = { CliSymlinkService.DEFAULT_LINK_PATH },
                                messageType = MessageType.Success,
                            )
                            doneAction()
                        }
                        CliInstallResult.CANCELLED -> {
                            installing = false
                        }
                        CliInstallResult.FAILURE -> {
                            notificationManager.sendNotification(
                                title = { it.getText("install_cli_tool_failed") },
                                messageType = MessageType.Error,
                            )
                            doneAction()
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    doneAction()
                },
            ) {
                Text(copywriter.getText("later"))
            }
        },
    )
}
