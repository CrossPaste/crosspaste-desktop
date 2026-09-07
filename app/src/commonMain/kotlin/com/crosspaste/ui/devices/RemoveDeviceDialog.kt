package com.crosspaste.ui.devices

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Backspace
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import org.koin.compose.koinInject

/**
 * Removing a device drops the pairing, so a misclick costs a full re-pair.
 * Confirm before calling [SyncManager.removeSyncHandler].
 */
@Composable
fun DeviceScope.RemoveDeviceDialog(onDismiss: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()

    val appSizeValue = LocalAppSizeValueState.current

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
                    imageVector = MaterialSymbols.Rounded.Backspace,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("remove_device"),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Text(
                text = "${getDeviceDisplayName()}\n\n${copywriter.getText("remove_device_confirm_desc")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
            )
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText("remove_device"),
                type = DialogButtonType.ERROR,
            ) {
                syncManager.removeSyncHandler(syncRuntimeInfo.appInstanceId)
                onDismiss()
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(copywriter.getText("cancel"))
            }
        },
    )
}
