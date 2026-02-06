package com.crosspaste.ui.devices

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Wifi_find
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUISize.enormous
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.titanic
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xLargeRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun SearchingNearbyDevices() {
    val copywriter = koinInject<GlobalCopywriter>()
    val infiniteTransition = rememberInfiniteTransition(label = "searching_animation")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "icon_scale",
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "icon_alpha",
    )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = xLargeRoundedCornerShape,
    ) {
        Column(
            modifier = Modifier.padding(xLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(titanic),
            ) {
                // Background Pulse Circle
                Box(
                    modifier =
                        Modifier
                            .size(enormous)
                            .scale(scale)
                            .alpha(1f - alpha) // Fade out as it scales up
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                )

                // Indeterminate Circular Progress
                CircularProgressIndicator(
                    modifier = Modifier.size(giant),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = tiny2X / 2,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                // Center Icon
                Icon(
                    imageVector = MaterialSymbols.Rounded.Wifi_find,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(enormous / 2)
                            .alpha(alpha),
                    // Pulsing alpha for the icon
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(xLarge))

            Text(
                text = copywriter.getText("searching_for_nearby_devices"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(tiny))

            Text(
                text = copywriter.getText("searching_for_nearby_devices_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
