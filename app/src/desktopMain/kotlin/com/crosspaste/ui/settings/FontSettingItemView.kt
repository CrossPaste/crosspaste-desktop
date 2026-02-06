package com.crosspaste.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Font_download
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.FontInfo
import com.crosspaste.ui.base.FontManager
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.rememberUserSelectedFont
import com.crosspaste.ui.theme.AppUIFont.menuItemTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontSettingItemView() {
    val fontManager = koinInject<FontManager>()
    val themeExt = LocalThemeExtState.current

    val currentFont by rememberUserSelectedFont()

    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingListItem(
            title = "font",
            subtitleContent = {
                Text(currentFont.name)
            },
            icon = IconData(MaterialSymbols.Rounded.Font_download, themeExt.amberIconColor),
            trailingContent = { Icon(MaterialSymbols.Rounded.Chevron_right, null) },
        ) {
            expanded = true
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .wrapContentSize(Alignment.TopEnd),
        ) {
            var allPossibleFonts by remember { mutableStateOf(listOf<FontInfo>()) }

            LaunchedEffect(Unit) {
                allPossibleFonts = fontManager.selectableFonts
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = -medium, y = zero),
                modifier = Modifier.heightIn(max = xxLarge * 8),
            ) {
                allPossibleFonts.forEachIndexed { _, fontInfo ->
                    val isSelected = fontInfo.name == currentFont.name
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
                                fontInfo.name,
                                style =
                                    menuItemTextStyle.copy(
                                        fontFamily = fontInfo.fontFamily,
                                    ),
                            )
                        },
                        onClick = {
                            fontManager.setFont(fontInfo.uri)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
