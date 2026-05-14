package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.NetworkProfileService
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
actual fun rememberNetworkDiscoveryBlocked(): State<Boolean> {
    val service = koinInject<NetworkProfileService>()
    val diagnosis = service.diagnosis.collectAsState()
    return remember { derivedStateOf { diagnosis.value.isLikelyBlocking() } }
}

@Composable
actual fun NetworkDiscoveryBlockedNotice() {
    val isBlocked by rememberNetworkDiscoveryBlocked()
    if (!isBlocked) return

    val service = koinInject<NetworkProfileService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val warning = LocalThemeExtState.current.warning

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = warning.container,
        shape = small2XRoundedCornerShape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = medium, vertical = small),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            Text(
                text = copywriter.getText("windows_network_discovery_blocked_warning"),
                style = MaterialTheme.typography.bodyMedium,
                color = warning.onContainer,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
            )
            TextButton(
                onClick = { service.openNetworkSettings() },
                contentPadding = PaddingValues(horizontal = tiny),
            ) {
                Text(
                    text = copywriter.getText("open_network_settings"),
                    color = warning.color,
                )
            }
        }
    }
}
