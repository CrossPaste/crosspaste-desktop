package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.remove
import com.crosspaste.ui.devices.DeviceViewProvider
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import org.koin.compose.koinInject

@Composable
fun BlackListDeviceView(
    syncInfo: SyncInfo,
    clickable: () -> Unit,
) {
    val deviceViewProvider = koinInject<DeviceViewProvider>()
    deviceViewProvider.SyncDeviceView(syncInfo = syncInfo) {
        PasteIconButton(
            size = large2X,
            onClick = clickable,
            modifier =
                Modifier
                    .background(Color.Transparent, CircleShape),
        ) {
            Icon(
                painter = remove(),
                contentDescription = "remove blacklist",
                tint = Color.Red,
                modifier = Modifier.size(large2X),
            )
        }
        Spacer(modifier = Modifier.width(medium))
    }
}
