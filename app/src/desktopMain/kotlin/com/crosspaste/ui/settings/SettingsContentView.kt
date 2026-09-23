package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUISize.medium

/**
 * The settings root is an index, not a form: two cards of entry rows and no
 * inline controls, so every page is reachable without scrolling.
 */
@Composable
fun SettingsContentView() {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(medium),
    ) {
        item {
            MainSettingsContentView()
        }

        item {
            ClipboardSettingsEntryView()
        }
    }
}
