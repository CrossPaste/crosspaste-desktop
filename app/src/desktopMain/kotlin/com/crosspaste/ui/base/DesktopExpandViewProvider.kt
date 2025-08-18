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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.settings.LocalSettingsScrollState
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.zero

class DesktopExpandViewProvider(
    override val copywriter: GlobalCopywriter,
) : ExpandViewProvider {

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    override fun ExpandView(
        state: ExpandableState,
        horizontalPadding: Dp,
        barBackground: Color,
        onBarBackground: Color,
        backgroundColor: Color,
        barContent: @Composable ExpandViewScope.() -> Unit,
        content: @Composable ExpandViewScope.() -> Unit,
    ) {
        val scrollState = LocalSettingsScrollState.current
        var cardBounds by remember { mutableStateOf<Rect?>(null) }

        val scope =
            remember(state) {
                object : ExpandViewScope {
                    override val state: ExpandableState = state
                }
            }

        val arrowRotation by animateFloatAsState(
            targetValue = if (state.isExpanded) 90f else 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )

        val elevation by animateDpAsState(
            targetValue = if (state.isHovered) tiny3X else zero,
            animationSpec = tween(300),
        )

        val arrowOffset by animateFloatAsState(
            targetValue = if (state.isExpandedOrHovered()) 8f else 0f,
            animationSpec = tween(300),
        )

        val bottomCornerRadius by animateDpAsState(
            targetValue = if (state.isExpanded) zero else tiny,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )

        val animatedShape =
            RoundedCornerShape(
                topStart = tiny,
                topEnd = tiny,
                bottomStart = bottomCornerRadius,
                bottomEnd = bottomCornerRadius,
            )

        LaunchedEffect(state.isExpanded) {
            if (state.isExpanded && scrollState != null && cardBounds != null) {
                val bounds = cardBounds!!

                // calculate the top position of the card in the scrollable content
                val cardTopInContent = bounds.top

                val targetScroll = (cardTopInContent).coerceAtLeast(0f).toInt()
                scrollState.animateScrollTo(
                    targetScroll,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                )
            }
        }

        HighlightedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .onGloballyPositioned { coordinates ->
                        // Save the bounds of the card for scrolling
                        cardBounds = coordinates.boundsInParent()
                    },
            shape = tinyRoundedCornerShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = backgroundColor,
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .combinedClickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                            onClick = { state.onToggle() },
                        ).onPointerEvent(PointerEventType.Enter) { state.enter() }
                        .onPointerEvent(PointerEventType.Exit) { state.exit() }
                        .graphicsLayer(
                            shadowElevation = elevation.value,
                            shape = animatedShape,
                            ambientShadowColor = onBarBackground,
                            spotShadowColor = onBarBackground,
                        ).background(barBackground, animatedShape)
                        .padding(horizontal = medium, vertical = small2X),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                scope.barContent()

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier =
                        Modifier
                            .size(large)
                            .graphicsLayer(
                                translationX = arrowOffset,
                                transformOrigin = TransformOrigin(1f, 0.5f),
                            ),
                ) {
                    Icon(
                        painter = chevronRight(),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .rotate(arrowRotation),
                        tint = onBarBackground,
                    )
                }
            }

            AnimatedVisibility(
                visible = state.isExpanded,
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
                            .background(backgroundColor),
                ) {
                    Column {
                        scope.content()
                    }
                }
            }
        }
    }
}
