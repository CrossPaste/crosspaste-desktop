package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        content = { Column(content = content) },
    )
}

@Composable
expect fun SettingListItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListItem(
    title: String,
    subtitle: String? = null,
    painter: Painter? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListItem(
    title: String,
    subtitleContent: @Composable (() -> Unit),
    icon: ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
)
