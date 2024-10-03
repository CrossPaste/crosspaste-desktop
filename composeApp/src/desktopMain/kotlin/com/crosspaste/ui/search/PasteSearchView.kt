package com.crosspaste.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.ui.CrossPasteTheme
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.ascSort
import com.crosspaste.ui.base.crosspasteIcon
import com.crosspaste.ui.base.descSort
import com.crosspaste.ui.base.enter
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.menuItemReminderTextStyle
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.darken
import com.crosspaste.ui.favoriteColor
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CrossPasteSearchWindowContent() {
    val density = LocalDensity.current
    val appInfo = koinInject<AppInfo>()
    val appSize = koinInject<AppSize>() as DesktopAppSize
    val copywriter = koinInject<GlobalCopywriter>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteSearchService = koinInject<PasteSearchService>()
    val appUpdateService = koinInject<AppUpdateService>()
    val logger = koinInject<KLogger>()
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
                    .clip(appSize.appRoundedCornerShape)
                    .size(appSize.searchWindowSize)
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
                            Key.N -> {
                                if (it.isCtrlPressed) {
                                    pasteSearchService.downSelectedIndex()
                                }
                                true
                            }
                            Key.P -> {
                                if (it.isCtrlPressed) {
                                    pasteSearchService.upSelectedIndex()
                                }
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
                        .size(appSize.searchWindowContentSize)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
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
                                        text = copywriter.getText("type_to_search_history"),
                                        style =
                                            TextStyle(
                                                fontWeight = FontWeight.Light,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
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
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
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
                                        focusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        disabledPlaceholderColor = Color.Transparent,
                                        errorPlaceholderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    ),
                                textStyle =
                                    TextStyle(
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 15.sp,
                                        lineHeight = 5.sp,
                                    ),
                            )

                            val textStyle =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.SansSerif,
                                    color = MaterialTheme.colorScheme.onBackground,
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
                                                                MaterialTheme.colorScheme.surface.copy(0.64f)
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
                                                    tint = MaterialTheme.colorScheme.primary,
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
                                                                MaterialTheme.colorScheme.surface.copy(0.64f)
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
                                                            MaterialTheme.colorScheme.primary
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
                                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
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
                                                    color = MaterialTheme.colorScheme.onBackground,
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
                                                            .background(MaterialTheme.colorScheme.surface),
                                                ) {
                                                    if (pasteSearchService.searchPasteType != null) {
                                                        MenuItem(copywriter.getText("all_types"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(null)
                                                            currentType = "all_types"
                                                            showTypes = false
                                                            focusRequester.requestFocus()
                                                        }
                                                        HorizontalDivider()
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
                                                            pasteSearchService.setPasteType(
                                                                PasteType.URL,
                                                            )
                                                            currentType = "link"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
                                                        }
                                                    }

                                                    if (currentType != "html") {
                                                        MenuItem(copywriter.getText("html"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(
                                                                PasteType.HTML,
                                                            )
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
                                                            pasteSearchService.setPasteType(
                                                                PasteType.IMAGE,
                                                            )
                                                            currentType = "image"
                                                            showTypes = false
                                                            focusRequester.requestFocus() // keep textField focus
                                                        }
                                                    }

                                                    if (currentType != "file") {
                                                        MenuItem(copywriter.getText("file"), textStyle, paddingValues) {
                                                            pasteSearchService.setPasteType(
                                                                PasteType.FILE,
                                                            )
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

                    Row(modifier = Modifier.size(appSize.searchWindowContentSize)) {
                        SearchListView {
                            pasteSearchService.clickSetSelectedIndex(it)
                            focusRequester.requestFocus()
                        }
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            thickness = 2.dp,
                        )
                        DetailPasteDataView()
                    }

                    Row(
                        modifier =
                            Modifier.height(40.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.darken(0.1f))
                                .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = crosspasteIcon(),
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
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                ),
                        )

                        if (appUpdateService.existNewVersion()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                modifier =
                                    Modifier.width(32.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                        .clickable {
                                            appUpdateService.jumpDownload()
                                        },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "new!",
                                    color = Color.White,
                                    style = menuItemReminderTextStyle,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        appWindowManager.getPrevAppName()?.let {
                            Text(
                                text = "${copywriter.getText("paste_to")} $it",
                                style =
                                    TextStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = FontFamily.SansSerif,
                                        color = MaterialTheme.colorScheme.onBackground,
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
