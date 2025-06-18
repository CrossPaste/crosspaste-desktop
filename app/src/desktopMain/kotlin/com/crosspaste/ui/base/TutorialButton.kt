package com.crosspaste.ui.base

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun TutorialButton() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.95f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
    )

    Row(
        modifier =
            Modifier
                .wrapContentWidth()
                .height(large2X)
                .clip(tiny3XRoundedCornerShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    uiSupport.openCrossPasteWebInBrowser("tutorial/pasteboard")
                    configManager.updateConfig("showTutorial", false)
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier =
                Modifier.padding(horizontal = tiny2X)
                    .scale(scale),
            text = copywriter.getText("newbie_tutorial"),
            color = Color.White,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = TextUnit.Unspecified,
                ),
        )
    }
}
