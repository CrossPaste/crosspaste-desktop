package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.getMessageImageVector
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.xLarge

@Composable
fun NotificationCard(
    toast: Message,
    onCancelTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appSizeValue = LocalAppSizeValueState.current

    val containerColor =
        when (toast.messageType) {
            MessageType.Error -> MaterialTheme.colorScheme.errorContainer
            MessageType.Info -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val contentColor = contentColorFor(containerColor)

    Surface(
        modifier =
            modifier
                .shadow(elevation = tiny2X, shape = MaterialTheme.shapes.medium)
                .widthIn(
                    min = appSizeValue.notificationViewMinWidth,
                    max = appSizeValue.notificationViewMaxWidth,
                ),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        tonalElevation = tiny2X / 2,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = toast.title,
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            supportingContent =
                toast.message?.takeIf { it.isNotBlank() }?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            leadingContent = {
                Icon(
                    imageVector = getMessageImageVector(toast.messageType.getMessageStyle()),
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
}
