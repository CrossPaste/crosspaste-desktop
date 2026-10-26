package com.crosspaste.ui.search.side

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.nonFocusable
import com.crosspaste.ui.paste.PasteTagScope
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tiny6X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val DOUBLE_CLICK_TIMEOUT_MS = 300L.milliseconds

/**
 * Keyboard focus ring drawn just outside a pill-shaped chip. Drawn outside the bounds so it never
 * covers the chip's own selected/hover edge, which stays the only filter indicator.
 */
@Composable
fun Modifier.keyboardHighlightRing(visible: Boolean): Modifier {
    if (!visible) return this
    val color = MaterialTheme.colorScheme.primary
    return drawBehind {
        val gap = tiny4X.toPx()
        val stroke = (tiny5X + tiny6X).toPx()
        val inset = gap + stroke / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + inset * 2, size.height + inset * 2),
            cornerRadius = CornerRadius(size.height / 2 + inset),
            style = Stroke(width = stroke),
        )
    }
}

@Composable
fun PasteTagScope.TagChip(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isHighlighted: Boolean = false,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
) {
    var clickJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()
    val palette = rememberTagChipPalette(tag.color)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val edge =
        when {
            isSelected -> TagChipEdge.SELECTED
            isHovered -> TagChipEdge.HOVERED
            else -> TagChipEdge.REST
        }

    FilterChip(
        selected = isSelected,
        // Single click triggers onEdit (tag filter); double click triggers onSelect (enter edit mode)
        onClick = {
            if (clickJob != null && clickJob?.isActive == true) {
                clickJob?.cancel()
                clickJob = null
                onSelect()
            } else {
                clickJob =
                    scope.launch {
                        delay(DOUBLE_CLICK_TIMEOUT_MS)
                        onEdit()
                        clickJob = null
                    }
            }
        },
        label = {
            TagLabel(palette.accent) { textModifier ->
                Text(
                    text = tag.name,
                    modifier = textModifier,
                    style = tagTextStyle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        border = palette.animatedBorder(edge),
        elevation = null,
        colors = palette.chipColors(),
        shape = CircleShape,
        interactionSource = interactionSource,
        modifier =
            modifier
                .keyboardHighlightRing(isHighlighted)
                .then(palette.animatedLip(edge))
                .height(xxLarge)
                .nonFocusable(),
    )
}

@Composable
fun PasteTagScope.EditableTagChip(onEditDone: suspend (String) -> Unit) {
    var name by remember(tag.id) { mutableStateOf(tag.name) }

    val scope = rememberCoroutineScope()

    val palette = rememberTagChipPalette(tag.color)
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    FilterChip(
        selected = false,
        onClick = {
        },
        label = {
            TagLabel(palette.accent) { textModifier ->
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier =
                        textModifier
                            .width(IntrinsicSize.Min)
                            .widthIn(min = large2X)
                            .focusRequester(focusRequester),
                    textStyle = tagTextStyle().copy(color = palette.content),
                    singleLine = true,
                    cursorBrush = SolidColor(palette.content),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                scope.launch {
                                    onEditDone(name)
                                }
                            },
                        ),
                    interactionSource = interactionSource,
                )
            }
        },
        trailingIcon = {
            Icon(
                imageVector = MaterialSymbols.Rounded.Close,
                contentDescription = "Delete",
                modifier =
                    Modifier
                        .size(small)
                        .clickable {
                            scope.launch {
                                onEditDone(name)
                            }
                        },
                tint = palette.closeIcon,
            )
        },
        // The name field has focus while editing, so keep the edge emphasized
        border = palette.animatedBorder(TagChipEdge.SELECTED),
        elevation = null,
        colors = palette.chipColors(),
        shape = CircleShape,
        // Clicking the chip outside the name field must not pull focus off it
        modifier = palette.animatedLip(TagChipEdge.SELECTED).height(xxLarge).nonFocusable(),
    )
}

/**
 * Colors derived from a tag's swatch: a tint of the swatch as the fill (flattened onto the
 * app surface, see [rememberTagChipPalette]), and a darker (light theme) or lighter (dark theme)
 * shade of it for the label, so the text stays readable on the tint while still reading as
 * the tag's color.
 */
@Immutable
private data class TagChipPalette(
    val accent: Color,
    val content: Color,
    val container: Color,
    val selectedContainer: Color,
    val restBorder: Color,
    val closeIcon: Color,
) {
    @Composable
    fun chipColors() =
        FilterChipDefaults.filterChipColors(
            containerColor = container,
            selectedContainerColor = selectedContainer,
            labelColor = content,
            selectedLabelColor = content,
            iconColor = content,
            selectedLeadingIconColor = content,
            selectedTrailingIconColor = content,
        )

    @Composable
    fun animatedBorder(edge: TagChipEdge): BorderStroke {
        val color by animateColorAsState(if (edge == TagChipEdge.REST) restBorder else accent)
        val width by animateDpAsState(
            when (edge) {
                TagChipEdge.REST -> tiny5X
                TagChipEdge.HOVERED -> tiny5X + tiny6X
                TagChipEdge.SELECTED -> tiny4X
            },
        )
        return BorderStroke(width, color)
    }

    /**
     * A crescent of the accent color peeking out under the chip's bottom edge, so an
     * emphasized edge reads heavier at the bottom (like a key's lip) instead of uniform.
     */
    @Composable
    fun animatedLip(edge: TagChipEdge): Modifier {
        val depth by animateDpAsState(if (edge == TagChipEdge.REST) zero else tiny5X)
        return Modifier.drawBehind {
            val depthPx = depth.toPx()
            if (depthPx > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, depthPx),
                    size = size,
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
        }
    }
}

// Hover sits one step below selection so the active filter stays distinguishable
private enum class TagChipEdge { REST, HOVERED, SELECTED }

@Composable
private fun rememberTagChipPalette(tagColor: Long): TagChipPalette {
    val isDark = LocalThemeState.current.isCurrentThemeDark
    val surface = MaterialTheme.colorScheme.surface
    return remember(tagColor, isDark, surface) {
        val accent = Color(tagColor.toInt())
        val content = if (isDark) lerp(accent, Color.White, 0.35f) else lerp(accent, Color.Black, 0.5f)
        // Fills are flattened onto the app surface instead of being left translucent: the search
        // window sits on an acrylic/blur material (macOS/Windows) or a plain container ground
        // (Linux), so a see-through tint would pick up the desktop behind the window and turn
        // muddy. Flattening keeps each chip a self-contained opaque pill, like the opaque cards
        // used elsewhere in the search window.
        TagChipPalette(
            accent = accent,
            content = content,
            container = accent.copy(alpha = if (isDark) 0.22f else 0.16f).compositeOver(surface),
            selectedContainer = accent.copy(alpha = if (isDark) 0.36f else 0.28f).compositeOver(surface),
            restBorder = accent.copy(alpha = if (isDark) 0.45f else 0.4f),
            closeIcon = content.copy(alpha = CLOSE_ICON_ALPHA),
        )
    }
}

@Composable
private fun tagTextStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)

/**
 * Color dot followed by the tag name. The dot is aligned to the text baseline rather than
 * centered in the line box, which sits visibly too high next to lowercase Latin text.
 */
@Composable
private fun TagLabel(
    accent: Color,
    text: @Composable (Modifier) -> Unit,
) {
    val dotCenterAboveBaseline =
        with(LocalDensity.current) { tagTextStyle().fontSize.toPx() * DOT_CENTER_ABOVE_BASELINE_EM }
    Row(horizontalArrangement = Arrangement.spacedBy(tiny2X)) {
        Box(
            modifier =
                Modifier
                    .alignBy { it.measuredHeight / 2 + dotCenterAboveBaseline.roundToInt() }
                    .size(tiny)
                    .background(color = accent, shape = CircleShape),
        )
        text(Modifier.alignBy(FirstBaseline))
    }
}

// Between the x-height center of Latin lowercase (~0.26em) and the center of CJK glyphs (~0.38em)
private const val DOT_CENTER_ABOVE_BASELINE_EM = 0.31f

// Keeps the edit chip's delete icon readable on the pale swatches (0.6 was too faint on yellow)
private const val CLOSE_ICON_ALPHA = 0.75f
