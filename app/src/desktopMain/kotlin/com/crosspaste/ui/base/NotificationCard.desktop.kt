package com.crosspaste.ui.base

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.theme.AppUISize.tiny2X

@Composable
actual fun NotificationCard(
    containerColor: Color,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val appSizeValue = LocalAppSizeValueState.current

    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier =
            modifier
                .widthIn(
                    min = appSizeValue.notificationViewMinWidth,
                    max = appSizeValue.notificationViewMaxWidth,
                ).graphicsLayer {
                    this.shape = shape
                    this.clip = true
                    this.shadowElevation = 8f * density
                    this.ambientShadowColor = Color.Black.copy(alpha = 0.1f)
                    this.spotShadowColor = Color.Black.copy(alpha = 0.2f)
                },
        shape = shape,
        color = containerColor,
        tonalElevation = tiny2X,
    ) {
        content()
    }
}
