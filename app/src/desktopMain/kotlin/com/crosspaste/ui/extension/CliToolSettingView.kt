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
import androidx.compose.ui.graphics.Color
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Terminal
import com.crosspaste.cli.CliInstallResult
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.cli.CliSymlinkState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.utils.getAppEnvUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Status/action row for the terminal command, rendered on every platform:
 * macOS offers install/repair of the /usr/local/bin symlink; Windows offers
 * adding the CLI's folder to the user PATH (except MSIX installs, whose
 * execution alias is installer-managed); Linux reports the installer-managed
 * wiring; BINARY_MISSING explains where the executable should come from
 * (dev build vs packaged payload).
 */
@Composable
fun CliToolSettingItem() {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val platform = koinInject<Platform>()
    val themeExt = LocalThemeExtState.current

    val state by cliSymlinkService.state.collectAsState()
    val isWindows = platform.isWindows()

    SettingListItem(
        title = cliToolTitleKey(state, isWindows),
        // Raw subtitle content instead of the key parameter: the
        // translocation/conflict/missing guidance is a full recovery
        // instruction and must not be ellipsized to a single line
        subtitleContent = {
            Text(
                text =
                    copywriter.getText(
                        cliToolSubtitleKey(state, isWindows, cliSymlinkService.runsFromAppImage),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        icon = IconData(MaterialSymbols.Rounded.Terminal, themeExt.greenIconColor),
        trailingContent = { CliToolTrailingContent(state, isWindows) },
    )
}

/**
 * The "Install Command Line Tool" framing only fits states where an in-app
 * symlink install exists (macOS); on Windows the action is a PATH edit and
 * everywhere else the row is a status line.
 */
private fun cliToolTitleKey(
    state: CliSymlinkState,
    isWindows: Boolean,
): String =
    when {
        isWindows -> "command_line"
        state == CliSymlinkState.PROBING ||
            state == CliSymlinkState.BINARY_MISSING ||
            state == CliSymlinkState.EXTERNALLY_MANAGED
        -> "command_line"
        else -> "install_cli_tool"
    }

private fun cliToolSubtitleKey(
    state: CliSymlinkState,
    isWindows: Boolean,
    runsFromAppImage: Boolean,
): String =
    when (state) {
        CliSymlinkState.TRANSLOCATED -> "cli_tool_translocated_desc"
        CliSymlinkState.CONFLICT -> "cli_tool_conflict_desc"
        CliSymlinkState.EXTERNALLY_MANAGED ->
            // AppImage mounts at a new temporary path every launch, so
            // "add the folder above to PATH" would be wrong guidance there
            if (runsFromAppImage) {
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
        CliSymlinkState.NOT_INSTALLED ->
            if (isWindows) "cli_tool_windows_path_desc" else "install_cli_tool_desc"
        CliSymlinkState.NEEDS_REPAIR ->
            // Windows: our folder is on the PATH but an earlier entry
            // shadows it with another crosspaste-cli
            if (isWindows) "cli_tool_windows_shadowed_desc" else "install_cli_tool_desc"
        else -> "install_cli_tool_desc"
    }

@Composable
private fun CliToolTrailingContent(
    state: CliSymlinkState,
    isWindows: Boolean,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    when (state) {
        CliSymlinkState.INSTALLED ->
            CliStatusText(
                text = copywriter.getText("cli_tool_installed"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        CliSymlinkState.NOT_INSTALLED, CliSymlinkState.NEEDS_REPAIR ->
            CliInstallButton(state, isWindows)
        CliSymlinkState.BINARY_MISSING ->
            CliStatusText(
                text = copywriter.getText("cli_binary_missing"),
                color = MaterialTheme.colorScheme.error,
            )
        // Explained via the state-dependent subtitle; deliberately no
        // action button — installing would create a doomed or
        // destructive link (translocated/conflict), or there is
        // simply nothing to act on (external wiring, still probing)
        CliSymlinkState.TRANSLOCATED, CliSymlinkState.CONFLICT -> Unit
        CliSymlinkState.EXTERNALLY_MANAGED, CliSymlinkState.PROBING -> Unit
    }
}

@Composable
private fun CliStatusText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun CliInstallButton(
    state: CliSymlinkState,
    isWindows: Boolean,
) {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()

    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }

    val actionKey =
        when {
            state == CliSymlinkState.NEEDS_REPAIR -> "repair"
            isWindows -> "add_to_path"
            else -> "install"
        }
    TextButton(
        enabled = !installing,
        onClick = {
            installing = true
            scope.launch {
                notifyInstallResult(cliSymlinkService.install(), isWindows, notificationManager)
                installing = false
            }
        },
    ) {
        Text(copywriter.getText(if (installing) "installing" else actionKey))
    }
}

private fun notifyInstallResult(
    result: CliInstallResult,
    isWindows: Boolean,
    notificationManager: NotificationManager,
) {
    when (result) {
        CliInstallResult.SUCCESS ->
            notificationManager.sendNotification(
                title = { it.getText("cli_tool_installed") },
                message = {
                    if (isWindows) {
                        it.getText("cli_tool_windows_path_added")
                    } else {
                        CliSymlinkService.DEFAULT_LINK_PATH
                    }
                },
                messageType = MessageType.Success,
            )
        CliInstallResult.CANCELLED -> Unit
        CliInstallResult.FAILURE ->
            notificationManager.sendNotification(
                title = { it.getText("install_cli_tool_failed") },
                messageType = MessageType.Error,
            )
    }
}
