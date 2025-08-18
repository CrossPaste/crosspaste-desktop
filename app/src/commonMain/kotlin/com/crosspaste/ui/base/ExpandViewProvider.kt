package com.crosspaste.ui.base

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge

data class ExpandableState(
    val isExpanded: Boolean,
    val isHovered: Boolean,
    val onToggle: () -> Unit,
    val enter: () -> Unit,
    val exit: () -> Unit,
) {
    fun isExpandedOrHovered(): Boolean = isExpanded || isHovered
}

@Composable
fun rememberExpandableState(defaultExpanded: Boolean = false): ExpandableState {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    var hovered by remember { mutableStateOf(false) }
    return remember(expanded, hovered) {
        ExpandableState(
            isExpanded = expanded,
            isHovered = hovered,
            onToggle = { expanded = !expanded },
            enter = { hovered = true },
            exit = { hovered = false },
        )
    }
}

interface ExpandViewScope {

    val state: ExpandableState
}

interface ExpandViewProvider {

    val copywriter: GlobalCopywriter

    @Composable
    fun ExpandView(
        state: ExpandableState = rememberExpandableState(),
        horizontalPadding: Dp = medium,
        barBackground: Color = AppUIColors.expandBarBackground,
        onBarBackground: Color =
            MaterialTheme.colorScheme.contentColorFor(
                AppUIColors.expandBarBackground,
            ),
        backgroundColor: Color = AppUIColors.generalBackground,
        barContent: @Composable ExpandViewScope.() -> Unit,
        content: @Composable ExpandViewScope.() -> Unit,
    )

    @Composable
    fun ExpandBarView(
        state: ExpandableState,
        title: String,
        onBarBackground: Color =
            MaterialTheme.colorScheme.contentColorFor(
                AppUIColors.expandBarBackground,
            ),
        icon: @Composable () -> Painter? = { null },
    ) {
        val iconScale by animateFloatAsState(
            targetValue = if (state.isExpandedOrHovered()) 1f else 0.8f,
            animationSpec = tween(300),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()?.let {
                Box(
                    modifier =
                        Modifier
                            .size(xLarge)
                            .graphicsLayer(
                                scaleX = iconScale,
                                scaleY = iconScale,
                                transformOrigin = TransformOrigin(0.5f, 0.5f),
                            ),
                ) {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        tint = onBarBackground,
                    )
                }
                Spacer(modifier = Modifier.width(tiny))
            }

            Text(
                text = copywriter.getText(title),
                style = AppUIFont.expandTitleTextStyle,
                color = onBarBackground,
            )
        }
    }
}
