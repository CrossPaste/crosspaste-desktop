package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.theme.AppUISize.huge

@Composable
fun SettingCheckboxView(
    list: List<String>,
    getCurrentCheckboxValue: (Int) -> Boolean,
    onChange: (Int, Boolean) -> Unit,
) {
    list.forEachIndexed { index, content ->
        SettingCheckboxItemView(
            content = content,
            getCurrentCheckboxValue = { getCurrentCheckboxValue(index) },
            onChange = { onChange(index, it) },
        )
    }
}

@Composable
fun SettingCheckboxItemView(
    content: String,
    getCurrentCheckboxValue: () -> Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .height(huge)
                .clickable {
                    val state = getCurrentCheckboxValue()
                    onChange(!state)
                },
        headlineContent = { Text(content, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            val imageVector =
                if (getCurrentCheckboxValue()) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.RadioButtonUnchecked
                }
            Icon(imageVector, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
