package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Language
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun LanguageSettingItemView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeExt = LocalThemeExtState.current
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingListItem(
            title = "language",
            subtitle = "current_language",
            icon = IconData(MaterialSymbols.Rounded.Language, themeExt.indigoIconColor),
        ) {
            expanded = true
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .wrapContentSize(Alignment.TopEnd),
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = -medium, y = zero),
            ) {
                val allLanguages = copywriter.getAllLanguages()
                allLanguages.forEach { language ->
                    val isSelected = language.abridge == copywriter.language()
                    DropdownMenuItem(
                        modifier =
                            Modifier
                                .height(xxLarge)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                ),
                        text = {
                            Text(
                                text = language.name,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                        onClick = {
                            copywriter.switchLanguage(language.abridge)
                            expanded = false
                        },
                        colors =
                            MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                }
            }
        }
    }
}
