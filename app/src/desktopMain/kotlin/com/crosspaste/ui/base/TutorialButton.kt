package com.crosspaste.ui.base

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

private const val PULSE_COUNT = 3

@Composable
fun TutorialButton() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    // A few pulses draw the eye; an endless one would keep the window rendering
    // every frame for as long as the button is visible.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(PULSE_COUNT) {
            scale.animateTo(0.95f, tween(1000, easing = FastOutSlowInEasing))
            scale.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
        }
    }

    TextButton(
        onClick = {
            uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard")
            configManager.updateConfig("showTutorial", false)
        },
    ) {
        Text(
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            text = copywriter.getText("getting_started"),
            color = MaterialTheme.colorScheme.primary,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = TextUnit.Unspecified,
                ),
        )
    }
}
