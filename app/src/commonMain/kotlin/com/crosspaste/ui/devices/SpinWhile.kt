package com.crosspaste.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Rotates the content continuously while [active]. The infinite transition is only
 * composed while [active], because a composed one keeps requesting frames even when
 * its value is ignored, which redraws the window at the display refresh rate.
 */
@Composable
fun Modifier.spinWhile(active: Boolean): Modifier {
    if (!active) return this
    val rotation by rememberInfiniteTransition(label = "SpinTransition").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "SpinAngle",
    )
    return graphicsLayer { rotationZ = rotation }
}
