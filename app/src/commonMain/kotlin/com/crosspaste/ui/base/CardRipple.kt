package com.crosspaste.ui.base

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Ripple for a single white card lying on the light grey content ground.
 *
 * The default ripple overlays onSurface grey, which sinks a pressed white card into the
 * ground (about #F0F0F0 on #F6F7F8). Tinting with primary instead gives a light blue press
 * that stays clearly separate from the ground and matches the app's blue action language.
 * Hover is disabled: desktop lifts the card with a shadow instead, and touch has no hover.
 */
val cardPressRipple: RippleConfiguration
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @ReadOnlyComposable
    get() =
        RippleConfiguration(
            color = MaterialTheme.colorScheme.primary,
            rippleAlpha =
                RippleAlpha(
                    pressedAlpha = 0.10f,
                    focusedAlpha = 0.10f,
                    draggedAlpha = 0.16f,
                    hoveredAlpha = 0f,
                ),
        )
