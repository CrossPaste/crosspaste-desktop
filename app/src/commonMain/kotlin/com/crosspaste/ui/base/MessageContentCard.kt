package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.getMessageImageVector
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.xLarge

@Composable
fun MessageContentCard(
    title: String,
    message: String?,
    messageType: MessageType,
    onCancel: (() -> Unit)? = null,
    contentColor: Color,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        supportingContent =
            message?.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent = {
            Icon(
                imageVector = getMessageImageVector(messageType.getMessageStyle()),
                contentDescription = null,
                modifier = Modifier.size(xLarge),
                tint = contentColor.copy(alpha = 0.8f),
            )
        },
        trailingContent =
            onCancel?.let {
                {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.size(xLarge),
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(large),
                            tint = contentColor.copy(alpha = 0.5f),
                        )
                    }
                }
            },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = contentColor,
                supportingColor = contentColor.copy(alpha = 0.7f),
                leadingIconColor = contentColor,
                trailingIconColor = contentColor,
            ),
    )
}
