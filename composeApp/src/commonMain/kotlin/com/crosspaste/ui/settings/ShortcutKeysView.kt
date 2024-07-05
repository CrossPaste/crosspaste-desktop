package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.WindowDecoration
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.PasteDialog
import com.crosspaste.ui.base.edit

@Composable
fun ShortcutKeysView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "Shortcut_Keys")
    ShortcutKeysContentView()
}

@Composable
fun ShortcutKeysContentView() {
    val scrollState = rememberScrollState()

    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier.verticalScroll(scrollState)
                    .fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("Paste")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("Paste_Local_Last")

                Divider(modifier = Modifier.padding(start = 15.dp))

                ShortcutKeyRow("Paste_Remote_Last")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("ShowMain")

                Divider(modifier = Modifier.padding(start = 15.dp))

                ShortcutKeyRow("ShowSearch")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                ShortcutKeyRow("SwitchMonitorPasteboard")

                Divider(modifier = Modifier.padding(start = 35.dp))

                ShortcutKeyRow("SwitchEncrypt")
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShortcutKeyRow(name: String) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val shortcutKeys = current.koin.get<ShortcutKeys>()
    val dialogService = current.koin.get<DialogService>()

    var hover by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(40.dp)
                .onPointerEvent(
                    eventType = PointerEventType.Enter,
                    onEvent = {
                        hover = true
                    },
                ).onPointerEvent(
                    eventType = PointerEventType.Exit,
                    onEvent = {
                        hover = false
                    },
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        settingsText(copywriter.getText(name))
        Spacer(modifier = Modifier.weight(1f))

        val shortcutKeysListener = current.koin.get<ShortcutKeysListener>()

        Icon(
            modifier =
                Modifier.size(16.dp)
                    .clickable {
                        dialogService.pushDialog(
                            PasteDialog(
                                key = name,
                                width = 300.dp,
                                title = "Please_directly_enter_the_new_shortcut_key_you_wish_to_set",
                            ) {
                                DisposableEffect(Unit) {
                                    shortcutKeysListener.editShortcutKeysMode = true
                                    onDispose {
                                        shortcutKeysListener.editShortcutKeysMode = false
                                    }
                                }

                                Column(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .wrapContentHeight(),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .height(60.dp)
                                                .padding(15.dp)
                                                .border(1.dp, MaterialTheme.colors.onSurface, RoundedCornerShape(5.dp)),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                modifier =
                                                    Modifier.size(16.dp),
                                                painter = edit(),
                                                contentDescription = "edit shortcut key",
                                                tint = MaterialTheme.colors.primary,
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            ShortcutKeyItemView(shortcutKeysListener.currentKeys)
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        DialogButtonsView(
                                            cancelAction = {
                                                dialogService.popDialog()
                                            },
                                            confirmAction = {
                                                shortcutKeys.update(name, shortcutKeysListener.currentKeys)
                                                shortcutKeysListener.currentKeys.clear()
                                                dialogService.popDialog()
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
            painter = edit(),
            contentDescription = "edit shortcut key",
            tint = if (hover) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
        )

        Spacer(modifier = Modifier.width(10.dp))

        ShortcutKeyItemView(shortcutKeys.shortcutKeysCore.keys[name] ?: listOf())
    }
}

@Composable
fun ShortcutKeyItemView(keys: List<KeyboardKey>) {
    Row(
        modifier = Modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, info ->
            KeyboardView(keyboardValue = info.name, backgroundColor = MaterialTheme.colors.surface)
            if (index != keys.size - 1) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "+",
                    color = MaterialTheme.colors.onBackground,
                    style =
                        TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = MaterialTheme.typography.body1.fontFamily,
                        ),
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
        }
    }
}
