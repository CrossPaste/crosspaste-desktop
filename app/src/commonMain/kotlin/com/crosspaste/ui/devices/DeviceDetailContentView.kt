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
 * not view-only; the default shows just the status tag.
 */
@Composable
fun DeviceScope.DeviceDetailContentView(
    headerActions: @Composable DeviceScope.() -> Unit = { SyncStateTag(refreshing = false) },
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(xLarge),
    ) {
        DeviceDetailHeaderView(headerActions)
        IncompatibleSection()
        SyncControlSection()
        DeviceInfoSection()
    }
}
