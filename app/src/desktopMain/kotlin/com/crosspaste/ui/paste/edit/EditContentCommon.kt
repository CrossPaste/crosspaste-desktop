package com.crosspaste.ui.paste.edit

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Save
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteContentEditor
import org.koin.compose.koinInject

/** Cmd+S on macOS, Ctrl+S elsewhere. */
fun KeyEvent.isSaveShortcut(isMac: Boolean): Boolean =
    type == KeyEventType.KeyDown &&
        key == Key.S &&
        if (isMac) isMetaPressed else isCtrlPressed

/** Notifies the save result; returns true when the edit was stored. */
fun NotificationManager.notifyEditOutcome(outcome: PasteContentEditor.EditOutcome): Boolean {
    val saved = outcome is PasteContentEditor.EditOutcome.Updated
    sendNotification(
        title = { it.getText(if (saved) "save_successful" else "save_failed") },
        messageType = if (saved) MessageType.Success else MessageType.Error,
    )
    return saved
}

@Composable
fun EditSaveButton(
    hasChanges: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val colorScheme = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = onSave,
        modifier = modifier,
        containerColor = if (hasChanges) colorScheme.primaryContainer else colorScheme.surfaceVariant,
        contentColor =
            if (hasChanges) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Save,
            contentDescription = copywriter.getText("save"),
        )
    }
}
