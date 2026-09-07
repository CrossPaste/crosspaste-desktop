package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUISize.xLarge

/**
 * [headerActions] fills the trailing slot of the header row. Platforms pass the
 * same refresh / menu controls they show on the list row so the detail page is
 * not view-only; the default shows just the status tag. The connecting
 * indicator is owned here so the header's platform icon recolors together with
 * the actions.
 */
@Composable
fun DeviceScope.DeviceDetailContentView(
    headerActions: @Composable DeviceScope.(ConnectingIndicator) -> Unit = { SyncStateTag(it.visible) },
) {
    val scrollState = rememberScrollState()
    val indicator = rememberConnectingIndicator()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(xLarge),
    ) {
        DeviceDetailHeaderView(indicator.visible) { headerActions(indicator) }
        IncompatibleSection()
        SyncControlSection()
        DeviceInfoSection()
    }
}
