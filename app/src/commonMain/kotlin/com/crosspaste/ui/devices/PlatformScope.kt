package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalThemeExtState
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
    val titleColor: Color,
    val subtitleColor: Color,
    val paddingValues: PaddingValues = PaddingValues(medium),
    val shape: Shape = mediumRoundedCornerShape,
    val isClickable: Boolean = true,
)

val myDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContainerColor = MaterialTheme.colorScheme.background,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

val myDeviceDetailStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContainerColor = MaterialTheme.colorScheme.background,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            isClickable = false,
        )

val tokenDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconContainerColor = MaterialTheme.colorScheme.background,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            paddingValues = PaddingValues(),
            shape = RectangleShape,
            isClickable = false,
        )

val nearbyDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = LocalThemeExtState.current.info.color,
            iconContainerColor = MaterialTheme.colorScheme.background,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
