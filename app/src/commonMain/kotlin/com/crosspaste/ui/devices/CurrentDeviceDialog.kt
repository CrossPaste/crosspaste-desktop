package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Devices
import com.crosspaste.app.AppInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.Server
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.InfoItem
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.utils.DeviceUtils
import org.koin.compose.koinInject

@Composable
fun CurrentDeviceDialog(onDismiss: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val appInfo = koinInject<AppInfo>()
    val deviceUtils = koinInject<DeviceUtils>()
    val platform = koinInject<Platform>()
    val server = koinInject<Server>()

    val appSizeValue = LocalAppSizeValueState.current

    val rows =
        remember(appInfo, platform, copywriter) {
            val port = server.port()
            listOf(
                "device_name" to deviceUtils.getDeviceName(),
                "user_name" to appInfo.userName,
                "app_version" to appInfo.displayVersion(),
                "device_id" to deviceUtils.getDeviceId(),
                "os" to "${platform.name} ${platform.version}",
                "arch" to platform.arch,
                "port" to if (port <= 0) copywriter.getText("unknown") else port.toString(),
            )
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(appSizeValue.dialogWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = large,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = large2X, vertical = large2X),
                verticalArrangement = Arrangement.spacedBy(medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(xLarge),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(medium))
                    Text(
                        modifier = Modifier.weight(1f),
                        text = copywriter.getText("current_device"),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = copywriter.getText("cancel"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = copywriter.getText("current_device_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = large2X, vertical = tiny)) {
                        rows.forEachIndexed { index, (key, value) ->
                            InfoItem(copywriter.getText(key), value)
                            if (index < rows.size - 1) {
                                HorizontalDivider(
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant
                                            .copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
