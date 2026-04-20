package com.crosspaste.ui.mouse

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.crosspaste.mouse.IpcEvent

/**
 * Surfaces the most recent daemon [IpcEvent.Warning] (e.g. missing input
 * monitoring / accessibility permission) as a modal dialog. When [warning]
 * is `null` the dialog is hidden — callers pass a derived value from the
 * manager's state so the dialog auto-dismisses once the warning clears.
 */
@Composable
fun MousePermissionDialog(
    warning: IpcEvent.Warning?,
    onDismiss: () -> Unit,
) {
    if (warning == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(warning.code) },
        text = { Text(warning.message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
