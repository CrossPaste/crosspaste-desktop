package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsContentView() {
    MainSettingsView()

    Spacer(modifier = Modifier.height(25.dp))

    NetSettingsView()

    Spacer(modifier = Modifier.height(25.dp))

    StoreSettingsView()

    Spacer(modifier = Modifier.height(25.dp))

    PasteboardSettingsView()
}
