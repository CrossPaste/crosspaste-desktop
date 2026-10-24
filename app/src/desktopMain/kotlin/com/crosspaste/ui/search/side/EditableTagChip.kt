package com.crosspaste.ui.search.side

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
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
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val DOUBLE_CLICK_TIMEOUT_MS = 300L.milliseconds

@Composable
fun PasteTagScope.TagChip(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
) {
    var clickJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()
    val palette = rememberTagChipPalette(tag.color)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

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
        leadingIcon = { TagColorDot(palette.accent) },
        label = {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        border = palette.animatedBorder(emphasized = isHovered || isSelected),
        elevation = null,
        colors = palette.chipColors(),
        shape = CircleShape,
        interactionSource = interactionSource,
        modifier = modifier.height(xxLarge).nonFocusable(),
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
        leadingIcon = { TagColorDot(palette.accent) },
        label = {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier =
                    Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = large2X)
                        .focusRequester(focusRequester),
                textStyle =
                    MaterialTheme.typography.bodySmall.copy(
                        color = palette.content,
                        fontWeight = FontWeight.Medium,
                    ),
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
                tint = palette.content.copy(alpha = 0.6f),
            )
        },
        // The name field has focus while editing, so keep the edge emphasized
        border = palette.animatedBorder(emphasized = true),
        elevation = null,
        colors = palette.chipColors(),
        shape = CircleShape,
        // Clicking the chip outside the name field must not pull focus off it
        modifier = Modifier.height(xxLarge).nonFocusable(),
    )
}

/**
 * Colors derived from a tag's swatch: a translucent tint of the swatch as the fill, and a
 * darker (light theme) or lighter (dark theme) shade of it for the label so the text stays
 * readable on the tint while still reading as the tag's color.
 */
@Immutable
private data class TagChipPalette(
    val accent: Color,
    val content: Color,
    val container: Color,
    val selectedContainer: Color,
    val restBorder: Color,
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
    fun animatedBorder(emphasized: Boolean): BorderStroke {
        val color by animateColorAsState(if (emphasized) accent else restBorder)
        val width by animateDpAsState(if (emphasized) tiny4X else tiny5X)
        return BorderStroke(width, color)
    }
}

@Composable
private fun rememberTagChipPalette(tagColor: Long): TagChipPalette {
    val isDark = LocalThemeState.current.isCurrentThemeDark
    return remember(tagColor, isDark) {
        val accent = Color(tagColor.toInt())
        TagChipPalette(
            accent = accent,
            content = if (isDark) lerp(accent, Color.White, 0.3f) else lerp(accent, Color.Black, 0.45f),
            container = accent.copy(alpha = if (isDark) 0.18f else 0.12f),
            selectedContainer = accent.copy(alpha = if (isDark) 0.32f else 0.24f),
            restBorder = accent.copy(alpha = if (isDark) 0.4f else 0.35f),
        )
    }
}

@Composable
private fun TagColorDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(small2X)
                .background(color = color, shape = CircleShape),
    )
}
