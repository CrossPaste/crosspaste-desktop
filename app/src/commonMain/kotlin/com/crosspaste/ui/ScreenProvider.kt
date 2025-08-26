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
    fun ExportScreen()

    @Composable
    fun HomeScreen()

    @Composable
    fun ImportScreen()

    @Composable
    fun NearbyDeviceDetailScreen()

    // openTopBar is used on mobile to expand the top bar
    // Desktop does not need to do anything
    @Composable
    fun PasteboardScreen(openTopBar: () -> Unit)

    @Composable
    fun PasteTextEditScreen()

    @Composable
    fun QRScreen()

    @Composable
    fun SettingsScreen()

    @Composable
    fun RecommendScreen()

    @Composable
    fun TokenView()

    @Composable
    fun ToastView()
}
