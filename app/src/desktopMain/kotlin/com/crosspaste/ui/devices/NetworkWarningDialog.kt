package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Warning
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.NetworkProfileService
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import org.koin.compose.koinInject

@Composable
fun NetworkWarningDialogHost() {
    val networkProfileService = koinInject<NetworkProfileService>()
    val visible by networkProfileService.isWarningDialogVisible.collectAsState()
    val diagnosis by networkProfileService.diagnosis.collectAsState()

    if (visible && diagnosis.isLikelyBlocking()) {
        NetworkWarningDialog(
            onOpenSettings = {
                networkProfileService.openNetworkSettings()
                networkProfileService.dismissWarning()
            },
            onDismiss = { networkProfileService.dismissWarning() },
        )
    }
}

@Composable
private fun NetworkWarningDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val appSizeValue = LocalAppSizeValueState.current
    val warning = LocalThemeExtState.current.warning

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        containerColor = warning.surface,
        titleContentColor = warning.onContainer,
        textContentColor = warning.onContainer,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = warning.color,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("network_warning"),
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
                verticalArrangement = Arrangement.spacedBy(medium),
            ) {
                Text(
                    text = copywriter.getText("windows_network_discovery_blocked_warning"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = warning.color,
                        contentColor = warning.onColor,
                    ),
            ) {
                Text(copywriter.getText("open_network_settings"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = warning.onContainer,
                    ),
            ) {
                Text(copywriter.getText("network_warning_dismiss"))
            }
        },
    )
}
