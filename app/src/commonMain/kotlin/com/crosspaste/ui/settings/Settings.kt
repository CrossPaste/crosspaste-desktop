package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.PainterData
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium

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
    icon: IconData? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListItem(
    title: String,
    subtitle: String? = null,
    painter: PainterData? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListItem(
    titleContent: @Composable (() -> Unit),
    subtitle: String? = null,
    icon: IconData? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListItem(
    title: String,
    subtitleContent: @Composable (() -> Unit),
    icon: IconData? = null,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.ChevronRight, null)
    },
    onClick: (() -> Unit)? = null,
)

@Composable
expect fun SettingListSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: IconData? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
)

@Composable
fun <T> SegmentedControlSettingsRow(
    title: String,
    options: List<T>,
    selectedOptionIndex: Int,
    onOptionSelected: (Int, T) -> Unit,
    modifier: Modifier = Modifier,
    icon: IconData? = null,
    twoLine: Boolean = false,
    optionLabel: (T) -> String = { it.toString() },
) {
    val segmentedRow: @Composable () -> Unit = {
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, item ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onOptionSelected(index, item) },
                    selected = index == selectedOptionIndex,
                    label = {
                        Text(
                            text = optionLabel(item),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }

    if (twoLine) {
        Column(
            modifier = modifier.fillMaxWidth().padding(medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    icon.IconContent()
                    Spacer(Modifier.width(medium))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(medium))
            segmentedRow()
        }
    } else {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(huge)
                    .padding(horizontal = medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    icon.IconContent()
                    Spacer(Modifier.width(medium))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(medium))
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.weight(1f),
            ) {
                options.forEachIndexed { index, item ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        onClick = { onOptionSelected(index, item) },
                        selected = index == selectedOptionIndex,
                        label = {
                            Text(
                                text = optionLabel(item),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
    }
}
