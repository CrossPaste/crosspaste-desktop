package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun DeviceScope.MyDeviceMenuButton() {
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()

    var expanded by remember { mutableStateOf(false) }

    var showEditDeviceDialog by remember { mutableStateOf(false) }

    if (showEditDeviceDialog) {
        EditNoteDialog {
            showEditDeviceDialog = false
        }
    }

    Box(
        modifier = Modifier.wrapContentSize(Alignment.TopEnd),
    ) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(xxLarge),
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(large2X),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceBright),
        ) {
            DropdownMenuItem(
                modifier = Modifier.height(xxxLarge),
                text = { Text(copywriter.getText("add_note")) },
                onClick = {
                    expanded = false
                    showEditDeviceDialog = true
                },
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(large),
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                    )
                },
            )

            DropdownMenuItem(
                modifier = Modifier.height(xxxLarge),
                text = {
                    Text(
                        copywriter.getText("remove_device"),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    val id = syncRuntimeInfo.appInstanceId
                    syncManager.removeSyncHandler(id)
                },
                leadingIcon = {
                    Icon(
                        modifier = Modifier.size(large),
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}
