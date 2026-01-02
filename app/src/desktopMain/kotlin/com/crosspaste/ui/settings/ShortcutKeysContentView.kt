package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_LOCAL_LAST
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_PLAIN_TEXT
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_PRIMARY_TYPE
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_REMOTE_LAST
import com.crosspaste.listener.DesktopShortcutKeys.Companion.SHOW_MAIN
import com.crosspaste.listener.DesktopShortcutKeys.Companion.SHOW_SEARCH
import com.crosspaste.listener.DesktopShortcutKeys.Companion.TOGGLE_ENCRYPT
import com.crosspaste.listener.DesktopShortcutKeys.Companion.TOGGLE_PASTEBOARD_MONITORING
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
fun ShortcutKeysContentView() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SectionHeader("paste_action")
        }

        item {
            SettingSectionCard {
                ShortcutKeyRow(PASTE_PLAIN_TEXT)
                HorizontalDivider()
                ShortcutKeyRow(PASTE_PRIMARY_TYPE)
                HorizontalDivider()
                ShortcutKeyRow(PASTE_LOCAL_LAST)
                HorizontalDivider()
                ShortcutKeyRow(PASTE_REMOTE_LAST)
            }
        }

        item {
            SectionHeader("toggle_window", topPadding = medium)
        }

        item {
            SettingSectionCard {
                ShortcutKeyRow(SHOW_MAIN)
                HorizontalDivider()
                ShortcutKeyRow(SHOW_SEARCH)
            }
        }

        item {
            SectionHeader("quick_action", topPadding = medium)
        }

        item {
            SettingSectionCard {
                ShortcutKeyRow(TOGGLE_PASTEBOARD_MONITORING)
                HorizontalDivider()
                ShortcutKeyRow(TOGGLE_ENCRYPT)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShortcutKeyRow(name: String) {
    val copywriter = koinInject<GlobalCopywriter>()
    val shortcutKeys = koinInject<ShortcutKeys>()
    val shortcutKeysCore by shortcutKeys.shortcutKeysCore.collectAsState()

    var showSetShortcutKeysDialog by remember { mutableStateOf(false) }

    if (showSetShortcutKeysDialog) {
        SetShortcutKeysDialog(name) {
            showSetShortcutKeysDialog = false
        }
    }

    SettingListItem(
        title = name,
        trailingContent = {
            shortcutKeysCore.keys[name]?.let { keys ->
                ShortcutKeyItemView(keys)
            } ?: run {
                Text(
                    text = copywriter.getText("unassigned"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        onClick = {
            showSetShortcutKeysDialog = true
        },
    )
}

@Composable
fun ShortcutKeyItemView(keys: List<KeyboardKey>) {
    Row(
        modifier = Modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, info ->
            KeyboardView(key = info.name)
            if (index != keys.size - 1) {
                Spacer(modifier = Modifier.width(tiny))
            }
        }
    }
}
