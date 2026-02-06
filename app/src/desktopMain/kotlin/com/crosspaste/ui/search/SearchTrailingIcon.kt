package com.crosspaste.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults.iconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Bookmark
import com.composables.icons.materialsymbols.rounded.Vertical_align_bottom
import com.composables.icons.materialsymbols.rounded.Vertical_align_top
import com.composables.icons.materialsymbols.roundedfilled.Bookmark
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteType.Companion.ALL_TYPES
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.base.MenuItemView
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun SearchTrailingIcon() {
    val copywriter = koinInject<GlobalCopywriter>()

    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()

    val searchBaseParams by pasteSearchViewModel.searchBaseParams.collectAsState()

    val density = LocalDensity.current

    var showTypes by remember { mutableStateOf(false) }

    var currentType = searchBaseParams.pasteType?.let { PasteType.fromType(it) }

    val textStyle =
        MaterialTheme.typography.labelLarge.copy(
            lineHeight = TextUnit.Unspecified,
        )

    val menuTexts =
        PasteType.TYPES
            .map { copywriter.getText(it.name) }
            .plus(copywriter.getText(ALL_TYPES))

    val paddingValues = PaddingValues(horizontal = small3X, vertical = tiny3X)

    val maxWidth = getFontWidth(menuTexts, textStyle, paddingValues)

    Row(
        modifier =
            Modifier
                .padding(horizontal = small3X)
                .wrapContentWidth()
                .height(huge),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GeneralIconButton(
            imageVector =
                if (searchBaseParams.sort) {
                    MaterialSymbols.Rounded.Vertical_align_bottom
                } else {
                    MaterialSymbols.Rounded.Vertical_align_top
                },
            desc = "sort_by_creation_time",
            colors =
                iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            buttonSize = xLarge,
            iconSize = large2X,
            shape = tiny2XRoundedCornerShape,
        ) {
            pasteSearchViewModel.switchSort()
        }

        Spacer(modifier = Modifier.width(tiny4X))

        GeneralIconButton(
            imageVector =
                if (searchBaseParams.favorite) {
                    MaterialSymbols.RoundedFilled.Bookmark
                } else {
                    MaterialSymbols.Rounded.Bookmark
                },
            desc = "whether_to_search_only_favorites",
            colors =
                iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            buttonSize = xLarge,
            iconSize = large2X,
            shape = tiny2XRoundedCornerShape,
        ) {
            pasteSearchViewModel.switchFavorite()
        }

        Spacer(modifier = Modifier.width(tiny2X))

        OutlinedButton(
            onClick = {
                showTypes = true
            },
            modifier = Modifier.height(28.dp),
            shape = tiny2XRoundedCornerShape,
            border =
                BorderStroke(
                    tiny5X,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                ),
            contentPadding = PaddingValues(horizontal = small3X, vertical = small3X / 2),
        ) {
            Text(
                text = currentType?.let { copywriter.getText(it.name) } ?: copywriter.getText(ALL_TYPES),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (showTypes) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        with(density) { zero.roundToPx() },
                        with(density) { xxxxLarge.roundToPx() },
                    ),
                onDismissRequest = {
                    if (showTypes) {
                        showTypes = false
                    }
                },
                properties =
                    PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .wrapContentSize()
                            .background(Color.Transparent)
                            .shadow(small),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .width(maxWidth)
                                .wrapContentHeight()
                                .clip(tiny2XRoundedCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceBright),
                    ) {
                        if (searchBaseParams.pasteType != null) {
                            MenuItemView(
                                text = copywriter.getText("all_types"),
                                textStyle = textStyle,
                                paddingValues = paddingValues,
                            ) {
                                pasteSearchViewModel.updatePasteType(null)
                                currentType = null
                                showTypes = false
                            }
                            HorizontalDivider()
                        }

                        PasteType.TYPES.forEach { pasteType ->
                            if (currentType != pasteType) {
                                MenuItemView(
                                    text = copywriter.getText(pasteType.name),
                                    textStyle = textStyle,
                                    paddingValues = paddingValues,
                                    background = MaterialTheme.colorScheme.surfaceBright,
                                ) {
                                    pasteSearchViewModel.updatePasteType(pasteType.type)
                                    currentType = pasteType
                                    showTypes = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
