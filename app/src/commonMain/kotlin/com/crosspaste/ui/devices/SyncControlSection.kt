package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.app.AppControl
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun DeviceScope.SyncControlSection() {
    val appControl = koinInject<AppControl>()
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()

    Column(verticalArrangement = Arrangement.spacedBy(small2X)) {
        SectionHeader("sync_control")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = medium, vertical = small2X)) {
                SyncSwitchRow(
                    title = copywriter.getText("allow_send_to"),
                    subtitle = syncRuntimeInfo.getDeviceDisplayName(),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    checked = !appControl.isSyncControlEnabled() || syncRuntimeInfo.allowSend,
                ) { allowSend ->
                    if (appControl.isSyncControlEnabled(true)) {
                        syncManager.updateAllowSend(syncRuntimeInfo.appInstanceId, allowSend)
                    }
                }

                Spacer(modifier = Modifier.height(small2X))

                SyncSwitchRow(
                    title = copywriter.getText("allow_receive_from"),
                    subtitle = syncRuntimeInfo.getDeviceDisplayName(),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    checked = !appControl.isSyncControlEnabled() || syncRuntimeInfo.allowReceive,
                ) { allowReceive ->
                    if (appControl.isSyncControlEnabled(true)) {
                        syncManager.updateAllowReceive(syncRuntimeInfo.appInstanceId, allowReceive)
                    }
                }
            }
        }
    }
}

@Composable
fun SyncSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(xxxxLarge),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.width(medium))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(
            modifier = Modifier.scale(0.8f),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
