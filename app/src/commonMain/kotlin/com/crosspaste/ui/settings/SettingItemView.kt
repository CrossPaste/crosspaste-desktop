package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.crosspaste.app.AppSize
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject

@Composable
fun SettingItemView(
    text: String,
    isFinalText: Boolean = false,
    height: Dp? = null,
    painter: Painter? = null,
    tint: Color =
        MaterialTheme.colorScheme.contentColorFor(
            AppUIColors.generalBackground,
        ),
    content: @Composable () -> Unit,
) {
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    val backgroundColor = AppUIColors.generalBackground

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(height ?: appSize.settingsItemHeight)
                .padding(horizontal = small2X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        painter?.let {
            Icon(
                modifier = Modifier.size(medium),
                painter = painter,
                contentDescription = text,
                tint = tint,
            )

            Spacer(modifier = Modifier.width(small2X))
        }

        // Text with scroll and gradient
        val scrollState = rememberScrollState()
        var showStartGradient by remember { mutableStateOf(false) }
        var showEndGradient by remember { mutableStateOf(false) }

        // Monitor scroll state changes
        LaunchedEffect(scrollState) {
            snapshotFlow {
                Triple(scrollState.value, scrollState.maxValue, scrollState.viewportSize)
            }
                .distinctUntilChanged()
                .collect { (value, maxValue, _) ->
                    // If scroll is possible, the content overflows the container
                    val canScroll = maxValue > 0

                    // More precise judgment logic
                    showStartGradient = canScroll && value > 0
                    showEndGradient = canScroll && value < maxValue
                }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Text content
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsText(
                    text =
                        if (isFinalText) {
                            text
                        } else {
                            copywriter.getText(text)
                        },
                )
            }

            // Left gradient overlay
            if (showStartGradient) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(xLarge)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            backgroundColor,
                                            backgroundColor.copy(alpha = 0.9f),
                                            backgroundColor.copy(alpha = 0.5f),
                                            backgroundColor.copy(alpha = 0f),
                                        ),
                                ),
                            ),
                )
            }

            // Right gradient overlay
            if (showEndGradient) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(xLarge)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            backgroundColor.copy(alpha = 0f),
                                            backgroundColor.copy(alpha = 0.5f),
                                            backgroundColor.copy(alpha = 0.9f),
                                            backgroundColor,
                                        ),
                                ),
                            ),
                )
            }
        }

        // Minimum spacing to ensure a gap between text and content
        Spacer(modifier = Modifier.width(tiny))

        // Content - use wrapContentWidth() to ensure full visibility
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
