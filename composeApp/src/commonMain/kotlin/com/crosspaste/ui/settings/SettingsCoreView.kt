package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

@Composable
fun SettingsCoreView() {
    val settingsViewProvider = koinInject<SettingsViewProvider>()

    settingsViewProvider.MainSettingsView()

    Spacer(modifier = Modifier.height(25.dp))

    settingsViewProvider.NetSettingsView()

    Spacer(modifier = Modifier.height(25.dp))

    settingsViewProvider.StoreSettingsView()

    Spacer(modifier = Modifier.height(25.dp))

    settingsViewProvider.PasteboardSettingsView()
}
