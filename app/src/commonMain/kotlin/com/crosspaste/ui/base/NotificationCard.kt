package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.notification.Message
import com.crosspaste.notification.getMessageImageVector
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.xLarge

@Composable
expect fun NotificationCard(
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
fun NotificationContentCard(
    notification: Message,
    onCancelTapped: () -> Unit,
    contentColor: Color,
) {
    ListItem(
        headlineContent = {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        supportingContent =
            notification.message?.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent = {
            Icon(
                imageVector = getMessageImageVector(notification.messageType.getMessageStyle()),
                contentDescription = null,
                modifier = Modifier.size(xLarge),
                tint = contentColor.copy(alpha = 0.8f),
            )
        },
        trailingContent = {
            IconButton(
                onClick = onCancelTapped,
                modifier = Modifier.size(xLarge),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(large),
                    tint = contentColor.copy(alpha = 0.5f),
                )
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
