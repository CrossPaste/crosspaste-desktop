package com.clipevery.ui.search

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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
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
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.dao.clip.ClipType
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.ClipeveryTheme
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.ClipTooltipAreaView
import com.clipevery.ui.base.KeyboardView
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.ascSort
import com.clipevery.ui.base.descSort
import com.clipevery.ui.base.enter
import com.clipevery.ui.base.getMenWidth
import com.clipevery.ui.base.starRegular
import com.clipevery.ui.base.starSolid
import com.clipevery.ui.darken
import com.clipevery.ui.favoriteColor
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import java.awt.event.KeyEvent

@Composable
fun ClipeveryAppSearchView(koinApplication: KoinApplication) {
    CompositionLocalProvider(
        LocalKoinApplication provides koinApplication,
    ) {
        ClipeverySearchWindow()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ClipeverySearchWindow() {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val clipSearchService = current.koin.get<ClipSearchService>()
    val appWindowManager = clipSearchService.appWindowManager
    val logger = current.koin.get<KLogger>()

    var lastInputTime by remember { mutableStateOf(0L) }

    val focusRequester = remember { FocusRequester() }

    val searchWindowDpSize by remember { mutableStateOf(appWindowManager.searchWindowDpSize) }

    LaunchedEffect(appWindowManager.showSearchWindow) {
        if (appWindowManager.showSearchWindow) {
            delay(200)
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    LaunchedEffect(clipSearchService.inputSearch) {
        val currentTime = System.currentTimeMillis()
        lastInputTime = currentTime
        if (clipSearchService.inputSearch.trim().isNotEmpty()) {
            delay(500)
        }
        if (lastInputTime == currentTime) {
            clipSearchService.search()
        }
    }

    LaunchedEffect(
        clipSearchService.searchFavorite,
        clipSearchService.searchSort,
        clipSearchService.searchClipType,
    ) {
        clipSearchService.search()
    }

    ClipeveryTheme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .clip(RoundedCornerShape(10.dp))
                    .size(searchWindowDpSize)
                    .padding(10.dp)
                    .onKeyEvent {
                        when (it.key) {
                            Key(KeyEvent.VK_ENTER) -> {
                                runBlocking {
                                    clipSearchService.toPaste()
                                }
                                true
                            }
                            Key(KeyEvent.VK_UP) -> {
                                clipSearchService.upSelectedIndex()
                                true
                            }
                            Key(KeyEvent.VK_DOWN) -> {
                                clipSearchService.downSelectedIndex()
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
                        .size(searchWindowDpSize.minus(DpSize(20.dp, 20.dp)))
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
                                value = clipSearchService.inputSearch,
                                onValueChange = { clipSearchService.updateInputSearch(it) },
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

                            var currentType by remember { mutableStateOf("All_Types") }

                            val menuTexts =
                                arrayOf(
                                    copywriter.getText("All_Types"),
                                    copywriter.getText("Text"),
                                    copywriter.getText("Link"),
                                    copywriter.getText("Html"),
                                    copywriter.getText("Image"),
                                    copywriter.getText("File"),
                                )

                            val paddingValues = PaddingValues(10.dp, 5.dp, 10.dp, 5.dp)

                            val maxWidth = getMenWidth(menuTexts, textStyle, paddingValues)

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Row(
                                    modifier = Modifier.width(80.dp + maxWidth).height(50.dp).padding(10.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ClipTooltipAreaView(
                                        text = copywriter.getText("Sort_by_creation_time"),
                                    ) {
                                        ClipIconButton(
                                            size = 20.dp,
                                            onClick = {
                                                clipSearchService.switchSort()
                                                focusRequester.requestFocus() // keep textField focus
                                            },
                                            modifier =
                                                Modifier
                                                    .background(Color.Transparent, CircleShape),
                                        ) {
                                            Icon(
                                                modifier = Modifier.size(20.dp),
                                                painter = if (clipSearchService.searchSort) descSort() else ascSort(),
                                                contentDescription = "Sort by creation time",
                                                tint = MaterialTheme.colors.primary,
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    ClipTooltipAreaView(
                                        text = copywriter.getText("Whether_to_search_only_favorites"),
                                    ) {
                                        ClipIconButton(
                                            size = 18.dp,
                                            onClick = {
                                                clipSearchService.switchFavorite()
                                                focusRequester.requestFocus() // keep textField focus
                                            },
                                            modifier =
                                                Modifier
                                                    .background(Color.Transparent, CircleShape),
                                        ) {
                                            Icon(
                                                modifier = Modifier.size(18.dp),
                                                painter = if (clipSearchService.searchFavorite) starSolid() else starRegular(),
                                                contentDescription = "Favorite",
                                                tint =
                                                    if (clipSearchService.searchFavorite) {
                                                        favoriteColor()
                                                    } else {
                                                        MaterialTheme.colors.primary
                                                    },
                                            )
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
                                                    if (clipSearchService.searchClipType != null) {
                                                        MenuItem(copywriter.getText("All_Types"), textStyle, paddingValues) {
                                                            clipSearchService.setClipType(null)
                                                            currentType = "All_Types"
                                                            showTypes = false
                                                        }
                                                        Divider()
                                                    }

                                                    MenuItem(copywriter.getText("Text"), textStyle, paddingValues) {
                                                        clipSearchService.setClipType(ClipType.TEXT)
                                                        currentType = "Text"
                                                        showTypes = false
                                                        focusRequester.requestFocus() // keep textField focus
                                                    }
                                                    MenuItem(copywriter.getText("Link"), textStyle, paddingValues) {
                                                        clipSearchService.setClipType(ClipType.URL)
                                                        currentType = "Link"
                                                        showTypes = false
                                                        focusRequester.requestFocus() // keep textField focus
                                                    }
                                                    MenuItem(copywriter.getText("Html"), textStyle, paddingValues) {
                                                        clipSearchService.setClipType(ClipType.HTML)
                                                        currentType = "Html"
                                                        showTypes = false
                                                        focusRequester.requestFocus() // keep textField focus
                                                    }
                                                    MenuItem(copywriter.getText("Image"), textStyle, paddingValues) {
                                                        clipSearchService.setClipType(ClipType.IMAGE)
                                                        currentType = "Image"
                                                        showTypes = false
                                                        focusRequester.requestFocus() // keep textField focus
                                                    }
                                                    MenuItem(copywriter.getText("File"), textStyle, paddingValues) {
                                                        clipSearchService.setClipType(ClipType.FILE)
                                                        currentType = "File"
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

                    Row(modifier = Modifier.size(searchWindowDpSize.minus(DpSize(20.dp, 120.dp)))) {
                        SearchListView {
                            clipSearchService.clickSetSelectedIndex(it)
                            focusRequester.requestFocus()
                        }
                        Divider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            thickness = 2.dp,
                        )
                        DetialClipDataView()
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
                            painter = painterResource("clipevery_icon.png"),
                            contentDescription = "Clipevery",
                            modifier =
                                Modifier.size(25.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        appWindowManager.getPrevAppName()?.let {
                            Text(
                                text = "${copywriter.getText("Paste_To")} $it",
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
                                        runBlocking {
                                            clipSearchService.toPaste()
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
