package com.crosspaste.ui.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.ui.CrossPasteTheme
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.ascSort
import com.crosspaste.ui.base.descSort
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.darken
import com.crosspaste.ui.favoriteColor
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CrossPasteSearchWindowContent() {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val appInfo = current.koin.get<AppInfo>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appWindowManager = current.koin.get<AppWindowManager>()
    val pasteSearchService = current.koin.get<PasteSearchService>()
    val logger = current.koin.get<KLogger>()
    val focusRequester = appWindowManager.searchFocusRequester

    val scope = rememberCoroutineScope()
    var lastInputTime by remember { mutableStateOf(0L) }

    LaunchedEffect(pasteSearchService.inputSearch) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        lastInputTime = currentTime
        if (pasteSearchService.inputSearch.trim().isNotEmpty()) {
            delay(500)
        }
        if (lastInputTime == currentTime) {
            pasteSearchService.search()
        }
    }

    LaunchedEffect(
        pasteSearchService.searchFavorite,
        pasteSearchService.searchSort,
        pasteSearchService.searchPasteType,
    ) {
        pasteSearchService.search()
    }

    CrossPasteTheme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .clip(RoundedCornerShape(10.dp))
                    .size(appWindowManager.searchWindowState.size)
                    .padding(10.dp)
                    .onKeyEvent {
                        when (it.key) {
                            Key.Enter -> {
                                scope.launch(mainDispatcher) {
                                    appWindowManager.setSearchCursorWait()
                                    pasteSearchService.toPaste()
                                    appWindowManager.resetSearchCursor()
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                pasteSearchService.upSelectedIndex()
                                true
                            }
                            Key.DirectionDown -> {
                                pasteSearchService.downSelectedIndex()
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(5.dp, RoundedCornerShape(10.dp))
                        .size(appWindowManager.searchWindowState.size.minus(DpSize(20.dp, 20.dp)))
                        .background(MaterialTheme.colors.background)
                        .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column {
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
                                value = pasteSearchService.inputSearch,
                                onValueChange = { pasteSearchService.updateInputSearch(it) },
                                keyboardOptions = KeyboardOptions.Default.copy(autoCorrect = true),
                                visualTransformation = VisualTransformation.None,
                                placeholder = {
                                    Text(
                                        modifier = Modifier.wrapContentSize(),
                                        text = "please input search",
                                        style =
                                            TextStyle(
                                                fontWeight = FontWeight.Light,
                                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                                                fontSize = 15.sp,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                isError = false,
                                singleLine = true,
                                colors =
                                    TextFieldDefaults.textFieldColors(
                                        textColor = MaterialTheme.colors.onBackground,
                                        disabledTextColor = Color.Transparent,
                                        backgroundColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colors.primary,
                                        errorCursorColor = Color.Red,
                                        focusedIndicatorColor = MaterialTheme.colors.primary,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                textStyle =
                                    TextStyle(
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colors.onBackground,
                                        fontSize = 15.sp,
                                        lineHeight = 5.sp,
                                    ),
                            )

                            val textStyle =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 15.sp,
                                )

                            var showTypes by remember { mutableStateOf(false) }

                            var currentType by remember { mutableStateOf("all_types") }

                            val menuTexts =
                                arrayOf(
                                    copywriter.getText("all_types"),
                                    copywriter.getText("text"),
                                    copywriter.getText("link"),
                                    copywriter.getText("html"),
                                    copywriter.getText("image"),
                                    copywriter.getText("file"),
                                )

                            val paddingValues = PaddingValues(10.dp, 5.dp, 10.dp, 5.dp)

                            val maxWidth = getMenWidth(menuTexts, textStyle, paddingValues)

                            var hoverSortIcon by remember { mutableStateOf(false) }

                            var hoverFavoritesIcon by remember { mutableStateOf(false) }

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
                                    PasteTooltipAreaView(
                                        modifier = Modifier.size(32.dp),
                                        text = copywriter.getText("sort_by_creation_time"),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier.size(32.dp)
                                                    .onPointerEvent(
                                                        eventType = PointerEventType.Enter,
                                                        onEvent = {
                                                            hoverSortIcon = true
                                                        },
                                                    )
                                                    .onPointerEvent(
                                                        eventType = PointerEventType.Exit,
                                                        onEvent = {
                                                            hoverSortIcon = false
                                                        },
                                                    ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (hoverSortIcon) {
                                                                MaterialTheme.colors.surface.copy(0.64f)
                                                            } else {
                                                                Color.Transparent
                                                            },
                                                        ),
                                            ) {}

                                            PasteIconButton(
                                                size = 20.dp,
                                                onClick = {
                                                    pasteSearchService.switchSort()
                                                    focusRequester.requestFocus() // keep textField focus
                                                },
                                                modifier =
                                                    Modifier
                                                        .background(Color.Transparent, CircleShape),
                                            ) {
                                                Icon(
                                                    modifier = Modifier.size(20.dp),
                                                    painter = if (pasteSearchService.searchSort) descSort() else ascSort(),
                                                    contentDescription = "Sort by creation time",
                                                    tint = MaterialTheme.colors.primary,
                                                )
                                            }
                                        }
                                    }

                                    PasteTooltipAreaView(
                                        modifier = Modifier.size(32.dp),
                                        text = copywriter.getText("whether_to_search_only_favorites"),
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier.size(32.dp)
                                                    .onPointerEvent(
                                                        eventType = PointerEventType.Enter,
                                                        onEvent = {
                                                            hoverFavoritesIcon = true
                                                        },
                                                    )
                                                    .onPointerEvent(
                                                        eventType = PointerEventType.Exit,
                                                        onEvent = {
                                                            hoverFavoritesIcon = false
                                                        },
                                                    ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier.fillMaxSize()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (hoverFavoritesIcon) {
                                                                MaterialTheme.colors.surface.copy(0.64f)
                                                            } else {
                                                                Color.Transparent
                                                            },
                                                        ),
                                            ) {}

                                            PasteIconButton(
                                                size = 18.dp,
                                                onClick = {
                                                    pasteSearchService.switchFavorite()
                                                    focusRequester.requestFocus() // keep textField focus
                                                },
                                                modifier =
                                                    Modifier
                                                        .background(Color.Transparent, CircleShape),
                                            ) {
                                                Icon(
                                                    modifier = Modifier.size(18.dp),
                                                    painter = if (pasteSearchService.searchFavorite) favorite() else noFavorite(),
                                                    contentDescription = "Favorite",
                                                    tint =
                                                        if (pasteSearchService.searchFavorite) {
                                                            favoriteColor()
                                                        } else {
                                                            MaterialTheme.colors.primary
                                                        },
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))
                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth().height(32.dp)
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colors.onBackground.copy(alpha = 0.12f),
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
                                            text = copywriter.getText(currentType),
                                            style =
                                                TextStyle(
                                                    fontWeight = FontWeight.Light,
                                                    fontFamily = FontFamily.SansSerif,
                                                    color = MaterialTheme.colors.onBackground,
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
                                                            .background(MaterialTheme.colors.surface),
                                                ) {
                                                    if (pasteSearchService.searchPasteType != null) {
                                                        MenuItem(copywriter.getText("all_types"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(null)
                                                            currentType = "all_types"
                                                            showTypes = false
                                                            focusRequester.requestFocus()
                                                        }
                                                        Divider()
                                                    }

                                                    if (currentType != "text") {
                                                        MenuItem(copywriter.getText("text"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(PasteType.TEXT)
                                                            currentType = "text"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
                                                        }
                                                    }

                                                    if (currentType != "link") {
                                                        MenuItem(copywriter.getText("link"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(PasteType.URL)
                                                            currentType = "link"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
                                                        }
                                                    }

                                                    if (currentType != "html") {
                                                        MenuItem(copywriter.getText("html"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(PasteType.HTML)
                                                            currentType = "html"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
                                                        }
                                                    }

                                                    if (currentType != "image") {
                                                        MenuItem(
                                                            copywriter.getText("image"),
                                                            textStyle,
                                                            paddingValues,
                                                        ) {
                                                            pasteSearchService.setPasteType(PasteType.IMAGE)
                                                            currentType = "image"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
                                                        }
                                                    }

                                                    if (currentType != "file") {
                                                        MenuItem(copywriter.getText("file"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(PasteType.FILE)
                                                            currentType = "file"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
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

                    Row(modifier = Modifier.size(appWindowManager.searchWindowState.size.minus(DpSize(20.dp, 120.dp)))) {
                        SearchListView {
                            pasteSearchService.clickSetSelectedIndex(it)
                            focusRequester.requestFocus()
                        }
                        Divider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            thickness = 2.dp,
                        )
                        DetialPasteDataView()
                    }

                    Row(
                        modifier =
                            Modifier.height(40.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colors.surface.darken(0.1f))
                                .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource("crosspaste_icon.png"),
                            contentDescription = "CrossPaste",
                            modifier =
                                Modifier.size(25.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "CrossPaste ${appInfo.appVersion}",
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 14.sp,
                                ),
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        appWindowManager.getPrevAppName()?.let {
                            Text(
                                text = "${copywriter.getText("paste_to")} $it",
                                style =
                                    TextStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily.SansSerif,
                                        color = MaterialTheme.colors.onBackground,
                                        fontSize = 14.sp,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier =
                                    Modifier.clickable {
                                        scope.launch(mainDispatcher) {
                                            appWindowManager.setSearchCursorWait()
                                            pasteSearchService.toPaste()
                                            appWindowManager.resetSearchCursor()
                                        }
                                    },
                            ) {
                                KeyboardView(keyboardValue = enter)
                            }
                        }
                    }
                }
            }
        }
    }
}
