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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.crosspaste.app.AppSize
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_LOCAL_LAST
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_PLAIN_TEXT
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_PRIMARY_TYPE
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_REMOTE_LAST
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SHOW_MAIN
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SHOW_SEARCH
import com.crosspaste.listen.DesktopShortcutKeys.Companion.TOGGLE_ENCRYPT
import com.crosspaste.listen.DesktopShortcutKeys.Companion.TOGGLE_PASTEBOARD_MONITORING
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.PasteDialogFactory
import com.crosspaste.ui.base.edit
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import org.koin.compose.koinInject

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
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(tinyRoundedCornerShape)
                        .background(AppUIColors.generalBackground),
            ) {
                ShortcutKeyRow(PASTE)
            }

            Spacer(modifier = Modifier.height(large2X))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(tinyRoundedCornerShape)
                        .background(AppUIColors.generalBackground),
            ) {
                ShortcutKeyRow(PASTE_PLAIN_TEXT)

                HorizontalDivider(modifier = Modifier.padding(start = small))

                ShortcutKeyRow(PASTE_PRIMARY_TYPE)

                HorizontalDivider(modifier = Modifier.padding(start = small))

                ShortcutKeyRow(PASTE_LOCAL_LAST)

                HorizontalDivider(modifier = Modifier.padding(start = small))

                ShortcutKeyRow(PASTE_REMOTE_LAST)
            }

            Spacer(modifier = Modifier.height(large2X))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(tinyRoundedCornerShape)
                        .background(AppUIColors.generalBackground),
            ) {
                ShortcutKeyRow(SHOW_MAIN)

                HorizontalDivider(modifier = Modifier.padding(start = small))

                ShortcutKeyRow(SHOW_SEARCH)
            }

            Spacer(modifier = Modifier.height(large2X))

            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(tinyRoundedCornerShape)
                        .background(AppUIColors.generalBackground),
            ) {
                ShortcutKeyRow(TOGGLE_PASTEBOARD_MONITORING)

                HorizontalDivider(modifier = Modifier.padding(start = small))

                ShortcutKeyRow(TOGGLE_ENCRYPT)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShortcutKeyRow(name: String) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val dialogService = koinInject<DialogService>()
    val pasteDialogFactory = koinInject<PasteDialogFactory>()
    val shortcutKeys = koinInject<ShortcutKeys>()

    var hover by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(appSize.settingsItemHeight)
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
                .padding(horizontal = small2X, vertical = tiny2X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsText(text = copywriter.getText(name))
        Spacer(modifier = Modifier.weight(1f))

        val shortcutKeysListener = koinInject<ShortcutKeysListener>()

        Icon(
            modifier =
                Modifier.size(medium)
                    .clickable {
                        dialogService.pushDialog(
                            pasteDialogFactory.createDialog(
                                key = name,
                                title = "please_directly_enter_the_new_shortcut_key_you_wish_to_set",
                            ) {
                                DisposableEffect(Unit) {
                                    shortcutKeysListener.editShortcutKeysMode = true
                                    onDispose {
                                        shortcutKeysListener.currentKeys.clear()
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
                                                .height(appSize.settingsItemHeight)
                                                .border(tiny5X, MaterialTheme.colorScheme.onSurface, tinyRoundedCornerShape),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .padding(horizontal = medium),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                modifier =
                                                    Modifier.size(medium),
                                                painter = edit(),
                                                contentDescription = "edit shortcut key",
                                                tint = MaterialTheme.colorScheme.primary,
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
                                                if (name != PASTE || shortcutKeysListener.currentKeys.isNotEmpty()) {
                                                    shortcutKeys.update(name, shortcutKeysListener.currentKeys)
                                                }
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
            tint =
                if (hover) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )

        Spacer(modifier = Modifier.width(small3X))

        shortcutKeys.shortcutKeysCore.value.keys[name]?.let { keys ->
            ShortcutKeyItemView(keys)
        } ?: run {
            Text(
                text = copywriter.getText("unassigned"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun ShortcutKeyItemView(keys: List<KeyboardKey>) {
    Row(
        modifier = Modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, info ->
            KeyboardView(keyboardValue = info.name, background = MaterialTheme.colorScheme.primary)
            if (index != keys.size - 1) {
                Spacer(modifier = Modifier.width(tiny2X))
                Text(
                    text = "+",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.width(tiny2X))
            }
        }
    }
}
