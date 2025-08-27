package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.platform.Platform

interface PlatformScope {

    val platform: Platform

    val platformBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val selectedPlatformBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.secondaryContainer

    fun getDeviceDisplayName(): String

    @Composable
    fun Modifier.hoverModifier(
        onEnter: () -> Unit = {},
        onExit: () -> Unit = {},
    ): Modifier = this
}
