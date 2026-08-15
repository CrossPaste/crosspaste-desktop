package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny

@Composable
fun SettingsContentView() {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SectionHeader("general")
        }

        item {
            MainSettingsContentView()
        }

        item {
            SectionHeader("advanced", topPadding = medium)
        }

        item {
            AdvancedSettingsContentView()
        }
    }
}
