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
import androidx.compose.ui.unit.dp
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.remove
import com.crosspaste.ui.devices.SyncDeviceView

@Composable
fun BlackListDeviceView(
    syncInfo: SyncInfo,
    clickable: () -> Unit,
) {
    SyncDeviceView(syncInfo = syncInfo) {
        PasteIconButton(
            size = 20.dp,
            onClick = clickable,
            modifier =
                Modifier
                    .background(Color.Transparent, CircleShape),
        ) {
            Icon(
                painter = remove(),
                contentDescription = "remove blacklist",
                tint = Color.Red,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
