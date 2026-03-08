package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.composables.icons.materialsymbols.rounded.Chevron_right
import com.composables.icons.materialsymbols.rounded.Font_download
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.FontManager
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.rememberUserSelectedFont
import com.crosspaste.ui.theme.AppUIFont.menuItemTextStyle
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import org.koin.compose.koinInject

@Composable
fun FontSettingItemView() {
    val fontManager = koinInject<FontManager>()
    val themeExt = LocalThemeExtState.current

    val currentFont by rememberUserSelectedFont()
    val fonts by fontManager.selectableFontsFlow.collectAsState()
    val isLoaded = fonts.size > 1

    var expanded by remember { mutableStateOf(false) }

    SettingListItem(
        title = "font",
        subtitleContent = {
            Text(currentFont.name)
        },
        icon = IconData(MaterialSymbols.Rounded.Font_download, themeExt.amberIconColor),
        trailingContent = {
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopEnd),
            ) {
                Icon(MaterialSymbols.Rounded.Chevron_right, null)

                if (expanded) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(tiny3X),
                            shadowElevation = 3.dp,
                            tonalElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            if (!isLoaded) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .width(200.dp)
                                            .padding(medium),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(medium),
                                        strokeWidth = AppUISize.tiny4X,
                                    )
                                }
                            } else {
                                val listState = rememberLazyListState()
                                val selectedIndex = fonts.indexOfFirst { it.name == currentFont.name }

                                LaunchedEffect(selectedIndex) {
                                    if (selectedIndex > 0) {
                                        listState.scrollToItem(selectedIndex)
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier =
                                        Modifier
                                            .width(200.dp)
                                            .heightIn(max = xxLarge * 8),
                                ) {
                                    items(
                                        items = fonts,
                                        key = { it.id },
                                    ) { fontInfo ->
                                        val isSelected = fontInfo.name == currentFont.name
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(xxLarge)
                                                    .background(
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.secondaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                        },
                                                    ).clickable {
                                                        fontManager.setFont(fontInfo.uri)
                                                        expanded = false
                                                    }.padding(horizontal = small3X),
                                        ) {
                                            Text(
                                                fontInfo.name,
                                                style =
                                                    menuItemTextStyle.copy(
                                                        fontFamily = fontInfo.fontFamily,
                                                    ),
                                                color =
                                                    if (isSelected) {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = MaterialSymbols.Rounded.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(small),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) {
        expanded = true
    }
}
