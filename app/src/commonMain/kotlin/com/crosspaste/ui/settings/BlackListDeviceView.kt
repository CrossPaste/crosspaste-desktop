package com.crosspaste.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Remove
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.devices.SyncDeviceView
import com.crosspaste.ui.devices.SyncScope
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScope.BlackListDeviceView() {
    val nearbyDeviceManager = koinInject<NearbyDeviceManager>()

    SyncDeviceView {
        GeneralIconButton(
            imageVector = MaterialSymbols.Rounded.Remove,
            desc = "remove_blacklist",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            nearbyDeviceManager.unblockDevice(syncInfo.appInfo.appInstanceId)
        }
    }
}
