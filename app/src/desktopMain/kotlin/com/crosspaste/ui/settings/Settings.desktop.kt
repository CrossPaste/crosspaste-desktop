package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.large2X
import org.koin.compose.koinInject

@Composable
actual fun SettingListItem(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    trailingContent: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    ListItem(
        modifier =
            Modifier.height(huge).then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            ),
        headlineContent = {
            Text(
                text = copywriter.getText(title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        text = copywriter.getText(it),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent =
            icon?.let {
                { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
actual fun SettingListItem(
    title: String,
    subtitle: String?,
    painter: Painter?,
    trailingContent: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    ListItem(
        modifier =
            Modifier.height(huge).then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            ),
        headlineContent = {
            Text(
                text = copywriter.getText(title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        text = copywriter.getText(it),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent =
            painter?.let {
                {
                    Icon(
                        modifier = Modifier.size(large2X),
                        painter = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
actual fun SettingListItem(
    titleContent: @Composable (() -> Unit),
    subtitle: String?,
    icon: ImageVector?,
    trailingContent: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    ListItem(
        modifier =
            Modifier.height(huge).then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            ),
        headlineContent = titleContent,
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        text = copywriter.getText(it),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent =
            icon?.let {
                { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
actual fun SettingListItem(
    title: String,
    subtitleContent: @Composable (() -> Unit),
    icon: ImageVector?,
    trailingContent: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    ListItem(
        modifier =
            Modifier.height(huge).then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            ),
        headlineContent = {
            Text(
                text = copywriter.getText(title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = subtitleContent,
        leadingContent =
            icon?.let {
                { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
actual fun SettingListSwitchItem(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    ListItem(
        modifier =
            Modifier.height(huge),
        headlineContent = { Text(copywriter.getText(title), style = MaterialTheme.typography.bodyMedium) },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        text = copywriter.getText(it),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent =
            icon?.let {
                { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        trailingContent = {
            Switch(
                modifier = Modifier.scale(0.8f),
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
