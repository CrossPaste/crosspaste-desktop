package com.crosspaste.ui.extension.sourcecontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Remove
import com.composables.icons.materialsymbols.rounded.Search
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

/**
 * Match rules: plain substrings compared case-insensitively against the
 * source name. The fallback for apps whose exact source cannot be picked.
 */
@Composable
fun SourceExclusionPatternsSection(
    patterns: List<String>,
    subtitle: String,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeExt = LocalThemeExtState.current

    var input by remember { mutableStateOf("") }
    val canSubmit = input.isNotBlank()
    // A duplicate is not added but the intent is already met, so clear either way.
    val submit = {
        if (canSubmit) {
            onAdd(input)
            input = ""
        }
    }

    SettingSectionCard {
        SettingListItem(
            title = "source_exclusion_patterns",
            subtitle = subtitle,
            icon = IconData(MaterialSymbols.Rounded.Search, themeExt.blueIconColor),
            trailingContent = null,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = xxxxLarge, end = medium, bottom = medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(medium),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = copywriter.getText("source_exclusion_pattern_hint"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = small2XRoundedCornerShape,
                modifier =
                    Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                submit()
                                true
                            } else {
                                false
                            }
                        },
            )
            FilledTonalButton(
                onClick = submit,
                enabled = canSubmit,
                modifier = Modifier.height(xxLarge),
                contentPadding = PaddingValues(horizontal = small2X),
            ) {
                Text(
                    text = copywriter.getText("add"),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        patterns.forEach { pattern ->
            HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
            PatternItem(pattern = pattern, onRemove = { onRemove(pattern) })
        }
    }
}

@Composable
private fun PatternItem(
    pattern: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = medium, vertical = tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(medium),
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(large),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pattern,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        GeneralIconButton(
            imageVector = MaterialSymbols.Rounded.Remove,
            desc = "source_exclusion_remove",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            onClick = onRemove,
        )
    }
}
