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
import com.crosspaste.utils.getAppEnvUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Status/action row for the terminal command, rendered on every platform:
 * macOS offers install/repair of the /usr/local/bin symlink; Windows/Linux
 * report the installer-managed wiring; BINARY_MISSING explains where the
 * executable should come from (dev build vs packaged payload).
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

    // The "Install Command Line Tool" framing only fits states where an
    // in-app install exists (macOS); everywhere else the row is a status line
    val titleKey =
        when (state) {
            CliSymlinkState.PROBING,
            CliSymlinkState.BINARY_MISSING,
            CliSymlinkState.EXTERNALLY_MANAGED,
            -> "command_line"
            else -> "install_cli_tool"
        }
    val subtitleKey =
        when (state) {
            CliSymlinkState.TRANSLOCATED -> "cli_tool_translocated_desc"
            CliSymlinkState.CONFLICT -> "cli_tool_conflict_desc"
            CliSymlinkState.EXTERNALLY_MANAGED ->
                // AppImage mounts at a new temporary path every launch, so
                // "add the folder above to PATH" would be wrong guidance there
                if (cliSymlinkService.runsFromAppImage) {
                    "cli_tool_appimage_desc"
                } else {
                    "cli_tool_external_desc"
                }
            CliSymlinkState.BINARY_MISSING ->
                if (getAppEnvUtils().isDevelopment()) {
                    "cli_binary_missing_dev_desc"
                } else {
                    "cli_binary_missing_desc"
                }
            else -> "install_cli_tool_desc"
        }
    SettingListItem(
        title = titleKey,
        // Raw subtitle content instead of the key parameter: the
        // translocation/conflict/missing guidance is a full recovery
        // instruction and must not be ellipsized to a single line
        subtitleContent = {
            Text(
                text = copywriter.getText(subtitleKey),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                CliSymlinkState.BINARY_MISSING -> {
                    Text(
                        text = copywriter.getText("cli_binary_missing"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // Explained via the state-dependent subtitle; deliberately no
                // action button — installing would create a doomed or
                // destructive link (translocated/conflict), or there is
                // simply nothing to act on (external wiring, still probing)
                CliSymlinkState.TRANSLOCATED, CliSymlinkState.CONFLICT -> Unit
                CliSymlinkState.EXTERNALLY_MANAGED, CliSymlinkState.PROBING -> Unit
            }
        },
    )
}
