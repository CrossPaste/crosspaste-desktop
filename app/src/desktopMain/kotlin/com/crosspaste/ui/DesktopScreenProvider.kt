package com.crosspaste.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.ui.base.RecommendContentView
import com.crosspaste.ui.base.ToastListView
import com.crosspaste.ui.devices.DeviceDetailContentView
import com.crosspaste.ui.devices.DevicesContentView
import com.crosspaste.ui.devices.QRContentView
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.ui.paste.PasteExportContentView
import com.crosspaste.ui.paste.PasteImportContentView
import com.crosspaste.ui.paste.PasteboardContentView
import com.crosspaste.ui.paste.edit.PasteTextEditContentView
import com.crosspaste.ui.settings.SettingsContentView
import com.crosspaste.ui.settings.ShortcutKeysContentView
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.zero

class DesktopScreenProvider(
    private val appSize: DesktopAppSize,
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
            Modifier
                .fillMaxSize()
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

    @Composable
    override fun TokenView() {
        TokenView(IntOffset(0, 0))
    }

    @Composable
    override fun ToastView() {
        val density = LocalDensity.current

        val yOffset by remember {
            mutableStateOf(-appSize.windowDecorationHeight + tiny3X)
        }

        ToastListView(
            IntOffset(
                with(density) { zero.roundToPx() },
                with(density) { yOffset.roundToPx() },
            ),
            enter =
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                ) +
                    fadeIn(
                        initialAlpha = 0f,
                        animationSpec = tween(150),
                    ),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                ) +
                    fadeOut(
                        animationSpec = tween(300),
                    ) +
                    shrinkVertically(
                        animationSpec = tween(300),
                    ),
        )
    }
}
