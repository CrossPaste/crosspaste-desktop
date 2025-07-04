package com.crosspaste.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppWindowManager
import com.crosspaste.ui.base.RecommendContentView
import com.crosspaste.ui.devices.DeviceDetailContentView
import com.crosspaste.ui.devices.DevicesContentView
import com.crosspaste.ui.devices.QRContentView
import com.crosspaste.ui.paste.PasteExportContentView
import com.crosspaste.ui.paste.PasteImportContentView
import com.crosspaste.ui.paste.PasteboardContentView
import com.crosspaste.ui.paste.edit.PasteTextEditContentView
import com.crosspaste.ui.settings.SettingsContentView
import com.crosspaste.ui.settings.ShortcutKeysContentView
import com.crosspaste.ui.theme.AppUISize.medium

class DesktopScreenProvider(
    private val appWindowManager: AppWindowManager,
) : ScreenProvider {

    @Composable
    override fun AboutScreen() {
        AboutContentView()
    }

    @Composable
    override fun CrossPasteScreen() {
        HomeScreen()
    }

    @Composable
    override fun DeviceDetailScreen() {
        DeviceDetailContentView()
    }

    @Composable
    override fun DevicesScreen() {
        DevicesContentView()
    }

    @Composable
    override fun ExportScreen() {
        PasteExportContentView()
    }

    @Composable
    override fun HomeScreen() {
        val screen by appWindowManager.screenContext.collectAsState()

        var modifier =
            Modifier.fillMaxSize()
                .padding(start = medium)
                .padding(bottom = medium)

        modifier =
            when (screen.screenType) {
                Pasteboard, Settings ->
                    modifier
                else ->
                    modifier.padding(end = medium)
            }

        Box(modifier = modifier) {
            WindowDecoration(screen.screenType.name)
            when (screen.screenType) {
                Pasteboard -> {
                    PasteboardScreen {}
                }
                Devices -> {
                    DevicesScreen()
                }
                QrCode -> {
                    QRScreen()
                }
                Debug -> {
                    DebugScreen()
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
                Recommend -> {
                    RecommendScreen()
                }
                else -> {}
            }
        }
    }

    @Composable
    override fun ImportScreen() {
        PasteImportContentView()
    }

    @Composable
    override fun PasteboardScreen(openTopBar: () -> Unit) {
        PasteboardContentView(openTopBar)
    }

    @Composable
    override fun PasteTextEditScreen() {
        PasteTextEditContentView()
    }

    @Composable
    override fun QRScreen() {
        QRContentView()
    }

    @Composable
    fun ShortcutKeysScreen() {
        ShortcutKeysContentView()
    }

    @Composable
    override fun SettingsScreen() {
        SettingsContentView()
    }

    @Composable
    override fun RecommendScreen() {
        RecommendContentView()
    }
}
