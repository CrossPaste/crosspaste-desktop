package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_svg
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getDateUtils
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
actual fun CrossPasteLogoView(
    size: Dp,
    color: Color,
    modifier: Modifier,
    enableDebugToggle: Boolean,
) {
    val configManager = koinInject<DesktopConfigManager>()
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val config by configManager.config.collectAsState()

    // Tap state management
    var tapCount by remember { mutableStateOf(0) }
    var firstTapTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(size / 4))
                .background(color)
                .size(size)
                .onSizeChanged { boxSize = it }
                .then(
                    if (enableDebugToggle) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // Hidden feature, no ripple effect
                        ) {
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
        val paddingPercent = 0.1f
        val paddingPx = minOf(boxSize.width, boxSize.height) * paddingPercent
        val paddingDp = with(LocalDensity.current) { paddingPx.toDp() }

        Icon(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingDp),
            painter = painterResource(Res.drawable.crosspaste_svg),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "CrossPaste Logo",
        )
    }
}
