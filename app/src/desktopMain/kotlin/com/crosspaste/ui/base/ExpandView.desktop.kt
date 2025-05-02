package com.crosspaste.ui.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun ExpandView(
    title: String,
    icon: @Composable (() -> Painter?),
    defaultExpand: Boolean,
    horizontalPadding: Dp,
    titleBackgroundColor: Color,
    onTitleBackgroundColor: Color,
    backgroundColor: Color,
    content: @Composable (() -> Unit),
) {
    val copywriter = koinInject<GlobalCopywriter>()
    var hover by remember { mutableStateOf(false) }
    var expand by remember { mutableStateOf(defaultExpand) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expand) 90f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
    )

    val elevation by animateDpAsState(
        targetValue = if (hover) 4.dp else 0.dp,
        animationSpec = tween(200),
    )

    val iconScale by animateFloatAsState(
        targetValue = if (hover || expand) 1f else 0.8f,
        animationSpec = tween(200),
    )

    val arrowOffset by animateFloatAsState(
        targetValue = if (hover || expand) 8f else 0f,
        animationSpec = tween(200),
    )

    HighlightedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = horizontalPadding),
        shape = RoundedCornerShape(8.dp),
        containerColor = backgroundColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .combinedClickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = { expand = !expand },
                    )
                    .onPointerEvent(PointerEventType.Enter) { hover = true }
                    .onPointerEvent(PointerEventType.Exit) { hover = false }
                    .graphicsLayer(
                        shadowElevation = elevation.value,
                        shape = RoundedCornerShape(8.dp),
                        ambientShadowColor = MaterialTheme.colorScheme.onSecondary,
                        spotShadowColor = MaterialTheme.colorScheme.onSecondary,
                    )
                    .background(
                        titleBackgroundColor,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()?.let {
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
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
                        tint = onTitleBackgroundColor,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = copywriter.getText(title),
                color = onTitleBackgroundColor,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        lineHeight = TextUnit.Unspecified,
                    ),
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .graphicsLayer(
                            translationX = arrowOffset,
                            transformOrigin = TransformOrigin(1f, 0.5f),
                        ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .rotate(arrowRotation),
                    tint = onTitleBackgroundColor,
                )
            }
        }

        AnimatedVisibility(
            visible = expand,
            enter =
                fadeIn(animationSpec = tween(300)) +
                    expandVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ),
            exit =
                fadeOut(animationSpec = tween(300)) +
                    shrinkVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .wrapContentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column {
                    content()
                }
            }
        }
    }
}
