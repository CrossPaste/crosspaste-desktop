package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsContentView() {
    MainSettingsView()

    Spacer(modifier = Modifier.height(10.dp))

    NetSettingsView()

    Spacer(modifier = Modifier.height(10.dp))

    StoreSettingsView()

    Spacer(modifier = Modifier.height(10.dp))

    PasteboardSettingsView()
}
