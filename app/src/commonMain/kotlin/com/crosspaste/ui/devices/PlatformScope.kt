package com.crosspaste.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X

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
    val border: BorderStroke? = null,
    val isClickable: Boolean = true,
    val nameMaxLines: Int = 1,
)

/** Device rows sit directly on [AppUIColors.contentBackground], so they share the section card look. */
private val deviceCardBorder: BorderStroke
    @Composable @ReadOnlyComposable
    get() = BorderStroke(tiny5X, AppUIColors.sectionCardBorder)

val myDeviceStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = AppUIColors.sectionCardBackground,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContainerColor = AppUIColors.contentBackground,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = deviceCardBorder,
        )

val myDeviceDetailStyle: DeviceStyle
    @Composable @ReadOnlyComposable
    get() =
        DeviceStyle(
            containerColor = AppUIColors.sectionCardBackground,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconContainerColor = AppUIColors.contentBackground,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = deviceCardBorder,
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
            containerColor = AppUIColors.sectionCardBackground,
            contentColor = LocalThemeExtState.current.info.color,
            iconContainerColor = AppUIColors.contentBackground,
            titleColor = MaterialTheme.colorScheme.onBackground,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = deviceCardBorder,
        )
