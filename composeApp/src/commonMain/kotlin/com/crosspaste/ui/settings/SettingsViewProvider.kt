package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable

interface SettingsViewProvider {

    @Composable
    fun AboutItemView()

    @Composable
    fun MainSettingsView()

    @Composable
    fun NetSettingsView()

    @Composable
    fun PasteboardSettingsView()

    @Composable
    fun StoreSettingsView()

    @Composable
    fun SettingsCoreView()
}
