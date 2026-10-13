package com.crosspaste.ui.base

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.theme.CrossPasteBrand
import com.crosspaste.utils.getDateUtils
import org.koin.compose.koinInject

private const val DRAW_DURATION_MILLIS = 1600

@Composable
actual fun CrossPasteLogoView(
    size: Dp,
    modifier: Modifier,
    enableDebugToggle: Boolean,
) {
    val configManager = koinInject<DesktopConfigManager>()

    val config by configManager.config.collectAsState()

    // Tap state management
    var tapCount by remember { mutableStateOf(0) }
    var firstTapTime by remember { mutableLongStateOf(0L) }

    // The glyph traces itself on first show and again on every tap.
    var drawRun by remember { mutableIntStateOf(0) }
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(drawRun) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(1f, tween(DRAW_DURATION_MILLIS, easing = FastOutSlowInEasing))
    }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(size / 4))
                .background(CrossPasteBrand.gradient)
                .size(size)
                .then(
                    if (enableDebugToggle) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // Hidden feature, no ripple effect
                        ) {
                            drawRun++
                            val currentTime = getDateUtils().nowEpochMilliseconds()
                            // Check if the 5-second window has expired
                            if (currentTime - firstTapTime > 5000) {
                                tapCount = 1
                                firstTapTime = currentTime
                            } else {
                                tapCount++
                            }

                            if (tapCount >= 5) {
                                configManager.updateConfig("enableDebugMode", !config.enableDebugMode)
                                // Reset state after triggering
                                tapCount = 0
                                firstTapTime = 0L
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
    ) {
        CrossPasteGlyph(
            progress = drawProgress.value,
            color = Color.White,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(size * 0.1f),
        )
    }
}
