package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.crosspaste.ui.LocalSmallSettingItemState
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxxxLarge

@Composable
fun SettingCheckboxView(
    list: List<String>,
    getCurrentCheckboxValue: (Int) -> Boolean,
    onChange: (Int, Boolean) -> Unit,
    trailingContent: @Composable ((Int) -> Unit)? = null,
) {
    list.forEachIndexed { index, content ->
        SettingCheckboxItemView(
            content = content,
            getCurrentCheckboxValue = { getCurrentCheckboxValue(index) },
            onChange = { onChange(index, it) },
            trailingContent = trailingContent?.let { { it(index) } },
        )
    }
}

@Composable
fun SettingCheckboxItemView(
    content: String,
    getCurrentCheckboxValue: () -> Boolean,
    onChange: (Boolean) -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val isSmallItem = LocalSmallSettingItemState.current
    val checked = getCurrentCheckboxValue()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (isSmallItem) xxxxLarge else huge)
                .clickable { onChange(!checked) }
                .padding(horizontal = medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(small2X),
        ) {
            CompactCheckbox(checked = checked)
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun CompactCheckbox(checked: Boolean) {
    val boxSize = medium
    val iconSize = small2X
    Box(
        modifier =
            Modifier
                .size(boxSize)
                .clip(tiny3XRoundedCornerShape)
                .then(
                    if (checked) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(tiny5X, MaterialTheme.colorScheme.outline, tiny3XRoundedCornerShape)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
