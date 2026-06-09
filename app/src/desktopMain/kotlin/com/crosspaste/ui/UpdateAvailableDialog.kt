package com.crosspaste.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.currentBackStackEntryAsState
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.UpdateState
import com.crosspaste.app.WindowsUpdateChannel
import com.crosspaste.app.WindowsZipUpdater
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

/**
 * Blocking update dialog for the Windows portable-zip channel. It appears over the main
 * window when a newer version exists (and hasn't been dismissed for that version), and
 * also when a previous apply FAILED — surfacing the failure prominently with a retry,
 * since on Windows a tray notification while the window is hidden is not delivered.
 * Dismissing ("Later") silences it until a newer release appears or the user checks for
 * updates again. Other channels (Store / Conveyor) are driven elsewhere and show nothing.
 */
@Composable
fun UpdateDialogHost() {
    val appUpdateService = koinInject<AppUpdateService>()
    val windowsZipUpdater = koinInject<WindowsZipUpdater>()

    if (windowsZipUpdater.channel != WindowsUpdateChannel.PORTABLE_ZIP) return

    val hasNewVersion by remember { appUpdateService.existNewVersion() }
        .collectAsState(initial = false)
    val lastVersion by appUpdateService.lastVersion.collectAsState()
    val updateState by windowsZipUpdater.updateState.collectAsState()
    val dismissedForVersion by windowsZipUpdater.promptDismissedForVersion.collectAsState()

    // Don't stack the modal on top of the changelog screen — its banner is the update
    // entry point there, so the dialog would be a redundant second one.
    val navController = LocalNavHostController.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onChangeLog = backStackEntry?.let { getRootRouteName(it.destination) } == ChangeLog.NAME

    val version = lastVersion?.toString() ?: return

    // Show when idle (offer the update) or after a failure (surface it + retry). While a
    // download is in flight the changelog banner shows progress, so don't pop the dialog.
    val state = updateState
    val failedReasonKey = (state as? UpdateState.Failed)?.reasonKey
    val relevant = state is UpdateState.Idle || state is UpdateState.Failed
    if (!hasNewVersion || !relevant || version == dismissedForVersion || onChangeLog) return

    val menuHelper = koinInject<MenuHelper>()

    UpdateAvailableDialog(
        version = version,
        failedReasonKey = failedReasonKey,
        onUpdateNow = { menuHelper.triggerPortableUpdate() },
        onLater = { windowsZipUpdater.dismissUpdatePrompt(version) },
    )
}

@Composable
private fun UpdateAvailableDialog(
    version: String,
    failedReasonKey: String?,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val appSizeValue = LocalAppSizeValueState.current

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onLater,
        title = {
            Text(
                text = copywriter.getText("update_available") + " v$version",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = tiny),
            ) {
                Text(
                    // On a previous failure show the reason; otherwise the normal blurb.
                    text = copywriter.getText(failedReasonKey ?: "update_available_dialog_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (failedReasonKey != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdateNow) {
                Text(copywriter.getText(if (failedReasonKey != null) "update_retry" else "update_now"))
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text(copywriter.getText("update_later"))
            }
        },
    )
}
