package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Expand_more
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge

@Composable
fun FilledDropdown(
    selectedIndex: Int,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOptionText = options.getOrNull(selectedIndex) ?: ""
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier.wrapContentWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            shape = tinyRoundedCornerShape,
            color = colorScheme.surface,
            border = BorderStroke(tiny5X, colorScheme.outlineVariant),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tiny2X),
                modifier =
                    Modifier
                        .clip(tinyRoundedCornerShape)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = small3X, vertical = tiny2X),
            ) {
                Text(
                    text = selectedOptionText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                )
                Icon(
                    imageVector = MaterialSymbols.Rounded.Expand_more,
                    contentDescription = "Expand",
                    modifier = Modifier.size(small),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colorScheme.surface,
            shape = tinyRoundedCornerShape,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color =
                                if (isSelected) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurface
                                },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                    trailingIcon =
                        if (isSelected) {
                            {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(small),
                                    tint = colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    contentPadding = PaddingValues(horizontal = small3X, vertical = tiny2X),
                    modifier = Modifier.height(xxLarge),
                )
            }
        }
    }
}
