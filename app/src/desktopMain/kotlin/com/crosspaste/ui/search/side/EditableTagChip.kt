package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.crosspaste.ui.paste.PasteTagScope
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PasteTagScope.TagChip(
    isSelected: Boolean,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
) {
    var clickJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()

    FilterChip(
        selected = isSelected,
        onClick = {
            if (clickJob != null && clickJob?.isActive == true) {
                clickJob?.cancel()
                clickJob = null
                onSelect()
            } else {
                clickJob =
                    scope.launch {
                        delay(300)
                        onEdit()
                        clickJob = null
                    }
            }
        },
        leadingIcon = {
            Box(
                modifier =
                    Modifier
                        .size(small2X)
                        .background(color = Color(tag.color.toInt()), shape = CircleShape),
            )
        },
        label = {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        border =
            if (isSelected) {
                FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = true,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                null
            },
        elevation = null,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        shape = CircleShape,
        modifier = Modifier.height(xxLarge),
    )
}

@Composable
fun PasteTagScope.EditableTagChip(onEditDone: suspend (String) -> Unit) {
    var name by remember(tag.id) { mutableStateOf(tag.name) }

    val scope = rememberCoroutineScope()

    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    FilterChip(
        selected = false,
        onClick = {
        },
        leadingIcon = {
            Box(
                modifier =
                    Modifier
                        .size(small2X)
                        .background(color = Color(tag.color.toInt()), shape = CircleShape),
            )
        },
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
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        },
        elevation = null,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        shape = CircleShape,
        modifier = Modifier.height(xxLarge),
    )
}
