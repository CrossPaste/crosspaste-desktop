package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape

@Composable
fun SyncScope.NearbyDeviceDetailContentView() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground)
                .padding(horizontal = medium)
                .padding(bottom = medium),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AppUIColors.appBackground)
                    .clip(tinyRoundedCornerShape),
        ) {
            NearbyDeviceDetailHeaderView()

            Spacer(Modifier.height(medium))

            NearbyDeviceDetailCoreView()
        }
    }
}
