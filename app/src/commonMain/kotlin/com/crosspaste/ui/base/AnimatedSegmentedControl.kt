package com.crosspaste.ui.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape

private data class ItemPosition(
    val width: Dp,
    val offsetX: Dp,
)

@Composable
fun <T> AnimatedSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelExtractor: (T) -> String = { it.toString() },
) {
    val density = LocalDensity.current
    val selectedIndex = items.indexOf(selectedItem)

    // State to store the positions and sizes of each item
    // Key is the index, Value is the position data
    var itemPositions by remember { mutableStateOf(mapOf<Int, ItemPosition>()) }

    // Calculate target animation values based on current measurements
    // Fallback to 0.dp if measurement isn't ready yet
    val targetWidth = itemPositions[selectedIndex]?.width ?: 0.dp
    val targetOffsetX = itemPositions[selectedIndex]?.offsetX ?: 0.dp

    // Animation specs: Using a spring for a bouncy, fluid feel
    val animationSpec = spring<Dp>(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.8f)
    // Alternative smooth linear animation:
    // val animationSpec = tween<Dp>(durationMillis = 300, easing = FastOutSlowInEasing)

    val animatedWidth by animateDpAsState(targetValue = targetWidth, animationSpec = animationSpec, label = "widthAnim")
    val animatedOffsetX by animateDpAsState(
        targetValue = targetOffsetX,
        animationSpec = animationSpec,
        label = "offsetAnim",
    )

    // Outer Container (The Track)
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(small2X))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .height(IntrinsicSize.Min) // Important: Let children dictate height
                .padding(tiny3X), // Padding between track and items
    ) {
        // Layer 1: The Animated Sliding Indicator (behind text)
        // Only show if we have valid measurements to avoid initial jump artifacts
        if (itemPositions.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .offset(x = animatedOffsetX)
                        .width(animatedWidth)
                        .fillMaxHeight()
                        .clip(tinyRoundedCornerShape)
                        .background(MaterialTheme.colorScheme.surface),
            )
        }

        // Layer 2: The Content Items (Text and clickable area)
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                SegmentedItemText(
                    label = labelExtractor(item),
                    isSelected = isSelected,
                    onClick = { onItemSelected(item) },
                    onPositioned = { size, positionX ->
                        // Capture measurements relative to the parent Row
                        val newWidth = with(density) { size.width.toDp() }
                        val newOffsetX = with(density) { positionX.toDp() }
                        // Only update if changed to avoid unnecessary recompositions
                        if (itemPositions[index]?.width != newWidth || itemPositions[index]?.offsetX != newOffsetX) {
                            itemPositions = itemPositions + (index to ItemPosition(newWidth, newOffsetX))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SegmentedItemText(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPositioned: (IntSize, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Text color animation
    val textColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = tween(200),
        label = "textColor",
    )

    // Hover background layer (only visible when NOT selected, on top of the track)
    val hoverBackgroundColor by animateColorAsState(
        targetValue =
            if (isHovered &&
                !isSelected
            ) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(150),
        label = "hoverBg",
    )

    Box(
        modifier =
            modifier
                // Measure position within parent Row
                .onGloballyPositioned { coordinates ->
                    onPositioned(coordinates.size, coordinates.positionInParent().x)
                }.clip(tinyRoundedCornerShape)
                // Apply hover background
                .background(hoverBackgroundColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = medium, vertical = tiny),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                ),
            color = textColor,
        )
    }
}
