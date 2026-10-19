package com.crosspaste.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getDateUtils
import org.koin.compose.koinInject

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

    AnimatedCrossPasteLogo(
        size = size,
        modifier =
            modifier.then(
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
    )
}
