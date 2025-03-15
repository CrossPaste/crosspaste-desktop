package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crosspaste.app.AppWindowManager
import com.crosspaste.ui.devices.DeviceDetailContentView
import com.crosspaste.ui.devices.DevicesContentView
import com.crosspaste.ui.devices.QRContentView
import com.crosspaste.ui.paste.PasteExportContentView
import com.crosspaste.ui.paste.PasteImportContentView
import com.crosspaste.ui.paste.PasteboardContentView
import com.crosspaste.ui.paste.edit.PasteTextEditContentView
import com.crosspaste.ui.settings.SettingsContentView
import com.crosspaste.ui.settings.ShortcutKeysContentView

class DesktopScreenProvider(
    private val appWindowManager: AppWindowManager,
) : ScreenProvider {

    @Composable
    override fun AboutScreen() {
        WindowDecoration("about")
        AboutContentView()
    }

    @Composable
    override fun CrossPasteScreen() {
        val screen by appWindowManager.screenContext.collectAsState()

        when (screen.screenType) {
            PastePreview,
            Devices,
            QrCode,
            Debug,
            -> {
                HomeScreen()
            }

            Settings -> {
                SettingsScreen()
            }

            ShortcutKeys -> {
                ShortcutKeysScreen()
            }

            Export -> {
                ExportScreen()
            }

            Import -> {
                ImportScreen()
            }

            About -> {
                AboutScreen()
            }

            DeviceDetail -> {
                DeviceDetailScreen()
            }

            PasteTextEdit -> {
                PasteTextEditScreen()
            }

            else -> {}
        }
    }

    @Composable
    override fun DeviceDetailScreen() {
        WindowDecoration("device_detail")
        DeviceDetailContentView()
    }

    @Composable
    override fun DevicesScreen() {
        DevicesContentView()
    }

    @Composable
    override fun ExportScreen() {
        WindowDecoration("export")
        PasteExportContentView()
    }

    @Composable
    override fun HomeScreen() {
        HomeWindowDecoration()
        TabsView()
    }

    @Composable
    override fun ImportScreen() {
        WindowDecoration("import")
        PasteImportContentView()
    }

    @Composable
    override fun PasteboardScreen(openTopBar: () -> Unit) {
        PasteboardContentView(openTopBar)
    }

    @Composable
    override fun PasteTextEditScreen() {
        WindowDecoration("text_edit")
        PasteTextEditContentView()
    }

    @Composable
    override fun QRScreen() {
        QRContentView()
    }

    @Composable
    fun ShortcutKeysScreen() {
        WindowDecoration("shortcut_keys")
        ShortcutKeysContentView()
    }

    @Composable
    override fun SettingsScreen() {
        WindowDecoration("settings")
        SettingsContentView()
    }
}
