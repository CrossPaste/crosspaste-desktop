package com.crosspaste.ui

import androidx.compose.runtime.Composable

interface ScreenProvider {

    @Composable
    fun AboutScreen()

    @Composable
    fun CrossPasteScreen()

    @Composable
    fun DeviceDetailScreen()

    @Composable
    fun DevicesScreen()

    @Composable
    fun HomeScreen()

    // openTopBar is used on mobile to expand the top bar
    // Desktop does not need to do anything
    @Composable
    fun PasteboardScreen(openTopBar: () -> Unit = {})

    @Composable
    fun PasteTextEditScreen()

    @Composable
    fun QRScreen()

    @Composable
    fun SettingsScreen()
}
