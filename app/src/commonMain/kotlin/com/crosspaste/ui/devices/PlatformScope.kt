package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.crosspaste.platform.Platform
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape

interface PlatformScope {

    val platform: Platform

    fun getDeviceDisplayName(): String
}

data class DeviceStyle(
    val containerColor: Color,
    val contentColor: Color,
    val iconContainerColor: Color,
    val iconContentColor: Color,
    val paddingValues: PaddingValues = PaddingValues(medium),
    val shape: Shape = mediumRoundedCornerShape,
    val isClickable: Boolean = true,
)

val myDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

val myDeviceDetailStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            isClickable = false,
        )

val tokenDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            paddingValues = PaddingValues(),
            shape = RectangleShape,
            isClickable = false,
        )

val nearbyDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
