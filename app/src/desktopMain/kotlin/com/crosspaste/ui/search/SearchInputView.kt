package com.crosspaste.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.PasteType
import com.crosspaste.db.paste.PasteType.Companion.ALL_TYPES
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.ascSort
import com.crosspaste.ui.base.descSort
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.base.search
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchInputView(requestFocus: () -> Unit) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val logger = koinInject<KLogger>()

    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()

    val inputSearch by pasteSearchViewModel.inputSearch.collectAsState()

    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val focusRequester = appWindowManager.searchFocusRequester

    LaunchedEffect(showSearchWindow) {
        if (showSearchWindow) {
            pasteSearchViewModel.resetSearch()
            delay(32)
            requestFocus()
        }
    }

    Row(
        modifier = Modifier.height(huge).fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            modifier =
                Modifier.focusTarget()
                    .focusRequester(focusRequester)
                    .onFocusEvent {
                        logger.debug { "onFocusEvent $it" }
                    }
                    .onFocusChanged {
                        logger.debug { "onFocusChanged $it" }
                    }
                    .fillMaxSize(),
            value = inputSearch,
            leadingIcon = {
                Icon(
                    painter = search(),
                    contentDescription = "search",
                )
            },
            trailingIcon = {
                searchTrailingIcon()
            },
            onValueChange = { pasteSearchViewModel.updateInputSearch(it) },
            keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = true),
            visualTransformation = VisualTransformation.None,
            placeholder = {
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = copywriter.getText("search_pasteboard"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            isError = false,
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = Color.Transparent,
                    errorTextColor = MaterialTheme.colorScheme.error,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    errorCursorColor = Color.Red,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.secondary,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = MaterialTheme.colorScheme.error,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    disabledPlaceholderColor = Color.Transparent,
                    errorPlaceholderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                ),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun searchTrailingIcon() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()

    val copywriter = koinInject<GlobalCopywriter>()

    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()

    val searchSort by pasteSearchViewModel.searchSort.collectAsState()

    val searchFavorite by pasteSearchViewModel.searchFavorite.collectAsState()

    val searchPasteType by pasteSearchViewModel.searchPasteType.collectAsState()

    val density = LocalDensity.current

    var showTypes by remember { mutableStateOf(false) }

    var currentType by remember { mutableStateOf<PasteType?>(null) }

    val focusRequester = appWindowManager.searchFocusRequester

    val textStyle =
        MaterialTheme.typography.labelLarge.copy(
            lineHeight = TextUnit.Unspecified,
        )

    val menuTexts =
        PasteType.TYPES
            .map { copywriter.getText(it.name) }
            .plus(copywriter.getText(ALL_TYPES))
            .toTypedArray()

    val paddingValues = PaddingValues(horizontal = small3X, vertical = tiny3X)

    val maxWidth = getMenWidth(menuTexts, textStyle, paddingValues)

    Row(
        modifier =
            Modifier.padding(horizontal = small3X)
                .wrapContentWidth()
                .height(huge),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PasteTooltipIconView(
            painter = if (searchSort) descSort() else ascSort(),
            contentDescription = "Sort by creation time",
            tint = MaterialTheme.colorScheme.primary,
            text = copywriter.getText("sort_by_creation_time"),
        ) {
            pasteSearchViewModel.switchSort()
            focusRequester.requestFocus() // keep textField focus
        }

        PasteTooltipIconView(
            painter = if (searchFavorite) favorite() else noFavorite(),
            contentDescription = "Favorite",
            tint = MaterialTheme.colorScheme.primary,
            text = copywriter.getText("whether_to_search_only_favorites"),
        ) {
            pasteSearchViewModel.switchFavorite()
            focusRequester.requestFocus() // keep textField focus
        }

        Spacer(modifier = Modifier.width(tiny2X))

        Row(
            modifier =
                Modifier.width(maxWidth)
                    .height(xxLarge)
                    .border(
                        tiny5X,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        tiny2XRoundedCornerShape,
                    )
                    .clickable {
                        showTypes = true
                        focusRequester.requestFocus() // keep textField focus
                    }
                    .padding(horizontal = small3X, vertical = small3X / 2),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentType?.let { copywriter.getText(it.name) } ?: copywriter.getText(ALL_TYPES),
                color = MaterialTheme.colorScheme.primary,
                style = textStyle,
            )
        }

        if (showTypes) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        with(density) { zero.roundToPx() },
                        with(density) { xxxLarge.roundToPx() },
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
                        if (searchPasteType != null) {
                            MenuItem(
                                text = copywriter.getText("all_types"),
                                textStyle = textStyle,
                                paddingValues = paddingValues,
                            ) {
                                pasteSearchViewModel.setPasteType(null)
                                currentType = null
                                showTypes = false
                                focusRequester.requestFocus()
                            }
                            HorizontalDivider()
                        }

                        PasteType.TYPES.forEach { pasteType ->
                            if (currentType != pasteType) {
                                MenuItem(
                                    text = copywriter.getText(pasteType.name),
                                    textStyle = textStyle,
                                    paddingValues = paddingValues,
                                    background = MaterialTheme.colorScheme.surfaceBright,
                                ) {
                                    pasteSearchViewModel.setPasteType(pasteType.type)
                                    currentType = pasteType
                                    showTypes = false
                                    focusRequester.requestFocus()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
