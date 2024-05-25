package com.clipevery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.listener.KeyboardKeyInfo
import com.clipevery.listener.ShortcutKeys
import com.clipevery.listener.ShortcutKeysListener
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.WindowDecoration
import com.clipevery.ui.base.ClipDialogWindowView
import com.clipevery.ui.base.DialogButtonsView
import com.clipevery.ui.base.KeyboardView
import com.clipevery.ui.base.edit

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
            Modifier.fillMaxSize()
                .background(MaterialTheme.colors.surface),
    ) {
        Column(
            modifier =
                Modifier.verticalScroll(scrollState)
                    .fillMaxSize()
                    .background(MaterialTheme.colors.surface),
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

    var hover by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }

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

        Icon(
            modifier =
                Modifier.size(16.dp)
                    .clickable {
                        showEdit = !showEdit
                    },
            painter = edit(),
            contentDescription = "edit shortcut key",
            tint = if (hover) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
        )

        Spacer(modifier = Modifier.width(10.dp))

        ShortcutKeyItemView(shortcutKeys.shortcutKeysCore.keys[name] ?: listOf())

        if (showEdit) {
            ClipDialogWindowView(
                title = copywriter.getText("Edit_Shortcut_Key"),
                size = DpSize(300.dp, 160.dp),
                onCloseRequest = { showEdit = false },
            ) {
                val shortcutKeysListener = current.koin.get<ShortcutKeysListener>()

                DisposableEffect(Unit) {
                    shortcutKeysListener.editShortcutKeysMode = true
                    onDispose {
                        shortcutKeysListener.editShortcutKeysMode = false
                    }
                }

                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colors.background),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(5.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            modifier = Modifier.padding(start = 15.dp),
                            text = copywriter.getText("Please_directly_enter_the_new_shortcut_key_you_wish_to_set"),
                            color = MaterialTheme.colors.onBackground,
                            style =
                                TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = MaterialTheme.typography.subtitle1.fontFamily,
                                ),
                        )
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(60.dp)
                                    .padding(15.dp)
                                    .border(1.dp, MaterialTheme.colors.onSurface, RoundedCornerShape(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp),
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

                        DialogButtonsView(
                            cancelAction = {
                                showEdit = false
                            },
                            confirmAction = {
                                shortcutKeys.update(name, shortcutKeysListener.currentKeys)
                                showEdit = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutKeyItemView(keys: List<KeyboardKeyInfo>) {
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
