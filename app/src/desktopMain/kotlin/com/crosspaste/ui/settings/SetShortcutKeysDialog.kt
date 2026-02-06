package com.crosspaste.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Keyboard
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import org.koin.compose.koinInject

@Composable
fun SetShortcutKeysDialog(
    name: String,
    onDismiss: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val shortcutKeys = koinInject<ShortcutKeys>()
    val shortcutKeysListener = koinInject<ShortcutKeysListener>()

    val appSizeValue = LocalAppSizeValueState.current

    DisposableEffect(Unit) {
        shortcutKeysListener.editShortcutKeysMode = true
        onDispose {
            shortcutKeysListener.currentKeys.clear()
            shortcutKeysListener.editShortcutKeysMode = false
        }
    }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Keyboard,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("shortcut_setting_title"),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = tiny),
                verticalArrangement = Arrangement.spacedBy(xLarge),
            ) {
                Text(
                    text = copywriter.getText("shortcut_setting_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(appSizeValue.settingsItemHeight)
                            .border(
                                tiny5X,
                                MaterialTheme.colorScheme.onSurface,
                                tinyRoundedCornerShape,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier =
                                Modifier.size(medium),
                            imageVector = MaterialSymbols.Rounded.Edit,
                            contentDescription = "edit shortcut key",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        ShortcutKeyItemView(shortcutKeysListener.currentKeys)
                    }
                }
            }
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText("confirm"),
                type = DialogButtonType.TONAL,
            ) {
                if (name != PASTE || shortcutKeysListener.currentKeys.isNotEmpty()) {
                    shortcutKeys.update(name, shortcutKeysListener.currentKeys)
                }
                shortcutKeysListener.currentKeys.clear()
                onDismiss()
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(copywriter.getText("cancel"))
            }
        },
    )
}
