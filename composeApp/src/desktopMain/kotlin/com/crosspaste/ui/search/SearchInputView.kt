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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.realm.paste.PasteType.Companion.ALL_TYPES
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.ascSort
import com.crosspaste.ui.base.descSort
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.theme.CrossPasteTheme.favoriteColor
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchInputView(requestFocus: () -> Unit) {
    val density = LocalDensity.current
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val logger = koinInject<KLogger>()

    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()

    val inputSearch by pasteSearchViewModel.inputSearch.collectAsState()

    val searchSort by pasteSearchViewModel.searchSort.collectAsState()

    val searchFavorite by pasteSearchViewModel.searchFavorite.collectAsState()

    val searchPasteType by pasteSearchViewModel.searchPasteType.collectAsState()

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
        modifier = Modifier.height(60.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
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
                onValueChange = { pasteSearchViewModel.updateInputSearch(it) },
                keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = true),
                visualTransformation = VisualTransformation.None,
                placeholder = {
                    Text(
                        modifier = Modifier.wrapContentSize(),
                        text = copywriter.getText("type_to_search_history"),
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 15.sp,
                            ),
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
                textStyle =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        lineHeight = 5.sp,
                    ),
            )

            val textStyle =
                TextStyle(
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )

            var showTypes by remember { mutableStateOf(false) }

            var currentType by remember { mutableStateOf<PasteType?>(null) }

            val menuTexts =
                PasteType.TYPES
                    .map { copywriter.getText(it.name) }
                    .plus(copywriter.getText(ALL_TYPES))
                    .toTypedArray()

            val paddingValues = PaddingValues(10.dp, 5.dp, 10.dp, 5.dp)

            val maxWidth = getMenWidth(menuTexts, textStyle, paddingValues)

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.width(94.dp + maxWidth).height(50.dp).padding(10.dp),
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
                        tint = MaterialTheme.colorScheme.favoriteColor(),
                        text = copywriter.getText("whether_to_search_only_favorites"),
                    ) {
                        pasteSearchViewModel.switchFavorite()
                        focusRequester.requestFocus() // keep textField focus
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().height(32.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    RoundedCornerShape(5.dp),
                                )
                                .clickable {
                                    showTypes = true
                                    focusRequester.requestFocus() // keep textField focus
                                }
                                .padding(10.dp, 5.dp, 10.dp, 5.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentType?.let { copywriter.getText(it.name) } ?: copywriter.getText(ALL_TYPES),
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                ),
                        )
                    }

                    if (showTypes) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset =
                                IntOffset(
                                    with(density) { (0.dp).roundToPx() },
                                    with(density) { (40.dp).roundToPx() },
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
                                        .shadow(15.dp),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .width(maxWidth)
                                            .wrapContentHeight()
                                            .clip(RoundedCornerShape(5.dp))
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
        }
    }
}
