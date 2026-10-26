package com.crosspaste.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.iconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Category
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteType.Companion.ALL_TYPES
import com.crosspaste.paste.getIconData
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.base.nonFocusable
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.model.RequestFocus
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

/**
 * Type filter at the end of the search field, an icon-only trigger like on mobile: the glyph of
 * the active type, or the all-types glyph when no type is filtered.
 *
 * The menu is driven from [PasteSelectionViewModel] (Enter opens it, Up/Down move, Enter picks)
 * and its popup never takes focus, so the search window's key router keeps handling the keys.
 */
@Composable
fun SearchTrailingIcon() {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val searchBaseParams by pasteSearchViewModel.searchBaseParams.collectAsState()
    val focusedElement by pasteSelectionViewModel.focusedElement.collectAsState()
    val typeMenuIndex by pasteSelectionViewModel.typeMenuIndex.collectAsState()

    val density = LocalDensity.current
    val typeFilterFocusRequester = remember { FocusRequester() }

    val currentType = searchBaseParams.pasteTypeList.singleOrNull()?.let { PasteType.fromType(it) }

    LaunchedEffect(Unit) {
        pasteSelectionViewModel.uiEvent.collect { event ->
            if (event == RequestFocus(FocusedElement.TYPE_FILTER)) {
                typeFilterFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier.padding(horizontal = small3X),
        contentAlignment = Alignment.Center,
    ) {
        // A focus target of its own, like the settings button; taking focus here moves it off
        // the search text field around it
        Box(
            modifier =
                Modifier
                    .focusRequester(typeFilterFocusRequester)
                    .onFocusChanged {
                        pasteSelectionViewModel.updateFocus(FocusedElement.TYPE_FILTER, it.isFocused)
                    }.focusable()
                    .keyboardHighlightRing(
                        visible = focusedElement == FocusedElement.TYPE_FILTER,
                        outset = zero,
                        cornerRadius = tiny2X,
                    ),
        ) {
            GeneralIconButton(
                imageVector = typeIcon(currentType),
                desc = currentType?.name ?: ALL_TYPES,
                colors =
                    iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                modifier = Modifier.nonFocusable(),
                shape = tiny2XRoundedCornerShape,
            ) {
                // Focus first, so the menu opens under the zone whose keys drive it
                typeFilterFocusRequester.requestFocus()
                pasteSelectionViewModel.openTypeMenu()
            }
        }

        typeMenuIndex?.let { highlightedIndex ->
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { xxxxLarge.roundToPx() }),
                onDismissRequest = { pasteSelectionViewModel.cancelTypeMenu() },
                properties =
                    PopupProperties(
                        focusable = false,
                        dismissOnClickOutside = true,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Max)
                            .shadow(small, tiny2XRoundedCornerShape)
                            .clip(tiny2XRoundedCornerShape)
                            .background(MaterialTheme.colorScheme.surfaceBright)
                            .padding(vertical = tiny3X),
                ) {
                    pasteSelectionViewModel.pasteTypeChoices.forEachIndexed { index, pasteType ->
                        TypeMenuItem(
                            label = copywriter.getText(pasteType?.name ?: ALL_TYPES),
                            icon = typeIcon(pasteType),
                            iconTint = pasteType?.getIconData()?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            selected = pasteType == currentType,
                            highlighted = index == highlightedIndex,
                        ) {
                            pasteSelectionViewModel.selectPasteType(pasteType)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun typeIcon(pasteType: PasteType?): ImageVector =
    pasteType?.getIconData()?.imageVector ?: MaterialSymbols.Rounded.Category

/** The active filter gets the container fill (as on mobile); the keyboard highlight gets the ring. */
@Composable
private fun TypeMenuItem(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(xxLarge)
                .padding(horizontal = tiny3X, vertical = tiny4X)
                .keyboardHighlightRing(visible = highlighted, outset = zero, cornerRadius = tiny2X)
                .clip(tiny2XRoundedCornerShape)
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = small3X),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(small2X),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(large),
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.contentColorFor(background),
            style = MaterialTheme.typography.labelLarge.copy(lineHeight = TextUnit.Unspecified),
        )
    }
}
