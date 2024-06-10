package com.clipevery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.clipevery.ui.AboutView
import com.clipevery.ui.ClipeveryTheme
import com.clipevery.ui.HomeView
import com.clipevery.ui.PageViewType
import com.clipevery.ui.base.DialogService
import com.clipevery.ui.base.MessageView
import com.clipevery.ui.base.ToastManager
import com.clipevery.ui.base.ToastView
import com.clipevery.ui.devices.DeviceDetailView
import com.clipevery.ui.devices.TokenView
import com.clipevery.ui.settings.SettingsView
import com.clipevery.ui.settings.ShortcutKeysView

@Composable
fun ClipeveryWindow(hideWindow: () -> Unit) {
    val current = LocalKoinApplication.current
    val toastManager = current.koin.get<ToastManager>()
    val dialogService = current.koin.get<DialogService>()

    val toast by toastManager.toast

    ClipeveryTheme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                hideWindow()
                            },
                            onTap = {
                                hideWindow()
                            },
                            onLongPress = {
                                hideWindow()
                            },
                            onPress = {},
                        )
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .fillMaxSize()
                    .padding(10.dp, 0.dp, 10.dp, 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(5.dp, RoundedCornerShape(10.dp), false)
                        .fillMaxSize()
                        .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {},
                                onTap = {},
                                onLongPress = {},
                                onPress = {},
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colors.background)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ClipeveryContent()
                }

                MessageView()

                toast?.let {
                    ToastView(toast = it) {
                        toastManager.cancel()
                    }
                }

                dialogService.dialogs.firstOrNull()?.content()
            }
        }
    }
}

@Composable
fun ClipeveryContent() {
    val currentPageViewContext = LocalPageViewContent.current

    TokenView()
    when (currentPageViewContext.value.pageViewType) {
        PageViewType.CLIP_PREVIEW,
        PageViewType.DEVICES,
        PageViewType.QR_CODE,
        PageViewType.DEBUG,
        -> {
            HomeView(currentPageViewContext)
        }
        PageViewType.SETTINGS -> {
            SettingsView(currentPageViewContext)
        }
        PageViewType.SHORTCUT_KEYS -> {
            ShortcutKeysView(currentPageViewContext)
        }
        PageViewType.ABOUT -> {
            AboutView(currentPageViewContext)
        }
        PageViewType.DEVICE_DETAIL -> {
            DeviceDetailView(currentPageViewContext)
        }
    }
}
