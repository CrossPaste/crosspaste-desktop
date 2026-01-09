package com.crosspaste.ui.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.crosspaste.notification.MessageType
import com.crosspaste.ui.theme.AppUISize.tiny4X

@Composable
fun AlertCard(
    title: String,
    message: String? = null,
    messageType: MessageType,
    shape: Shape = MaterialTheme.shapes.large,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = messageType.getMessageColor()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = shape,
        tonalElevation = tiny4X,
    ) {
        MessageContentCard(
            title = title,
            message = message,
            messageType = messageType,
            contentColor = contentColor,
        )
    }
}
