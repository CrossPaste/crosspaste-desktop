package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.crosspaste.platform.Platform
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape

interface PlatformScope {

    val platform: Platform

    fun getDeviceDisplayName(): String
}

data class DeviceStyle(
    val containerColor: Color,
    val contentColor: Color,
    val iconContentColor: Color,
    val shape: Shape = mediumRoundedCornerShape,
    val isClickable: Boolean = true,
)

val myDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
        )

val myDeviceDetailStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
            isClickable = false,
        )

val tokenDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            isClickable = false,
        )

val nearbyDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
        )
