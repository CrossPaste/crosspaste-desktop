package com.crosspaste.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import com.crosspaste.LocalKoinApplication
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.WindowDecoration
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.arrowDown
import com.crosspaste.ui.base.arrowLeft
import com.crosspaste.ui.base.arrowRight
import com.crosspaste.ui.base.arrowUp
import com.crosspaste.ui.base.bolt
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.language
import com.crosspaste.ui.base.palette
import com.crosspaste.ui.base.rocket
import com.crosspaste.ui.base.shield
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
fun SettingsTextStyle() =
    TextStyle(
        textAlign = TextAlign.Start,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        fontFamily = FontFamily.SansSerif,
        color = MaterialTheme.colors.onBackground,
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val configManager = current.koin.get<ConfigManager>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val pasteboardService = current.koin.get<PasteboardService>()
    var hasBeenClicked by remember { mutableStateOf(false) }
    var showMoreLanguage by remember { mutableStateOf(false) }

    var animationPhase by remember { mutableStateOf(0) }

    var languagePosition by remember { mutableStateOf(Offset.Zero) }
    var languageSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }
    var languageOnDismissTime by remember { mutableStateOf(0L) }

    val languageArrow: Painter =
        when (animationPhase) {
            0 -> arrowDown()
            1 -> arrowLeft()
            2 -> arrowUp()
            3 -> arrowRight()
            else -> arrowDown()
        }

    val density = LocalDensity.current

    LaunchedEffect(showMoreLanguage, hasBeenClicked) {
        if (hasBeenClicked) {
            animationPhase = (animationPhase + 1) % 4
            delay(100) // delay for the intermediate phase (arrowLeft)
            animationPhase = (animationPhase + 1) % 4
        }
    }

    val scrollState = rememberScrollState()

    var isScrolling by remember { mutableStateOf(false) }
    var scrollJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect {
            isScrolling = true
            scrollJob?.cancel()
            scrollJob =
                coroutineScope.launch(CoroutineName("HiddenScroll")) {
                    delay(500)
                    isScrolling = false
                }
        }
    }

    WindowDecoration(currentPageViewContext, "settings")

    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier.verticalScroll(scrollState)
                    .fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier.wrapContentSize()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colors.background),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = language(),
                        contentDescription = "language",
                        tint = MaterialTheme.colors.primary,
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    settingsText(copywriter.getText("language"))

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier =
                            Modifier.padding(6.dp).wrapContentSize()
                                .combinedClickable(
                                    interactionSource = MutableInteractionSource(),
                                    indication = null,
                                    onClick = {
                                        val currentTimeMillis = Clock.System.now().toEpochMilliseconds()
                                        if (currentTimeMillis - languageOnDismissTime > 500) {
                                            showMoreLanguage = !showMoreLanguage
                                            hasBeenClicked = true
                                        }
                                    },
                                ).onGloballyPositioned { coordinates ->
                                    languagePosition = coordinates.localToWindow(Offset.Zero)
                                    languageSize = coordinates.size.toSize()
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        settingsText(copywriter.getText("current_language"))

                        Icon(
                            modifier =
                                Modifier
                                    .padding(5.dp, 0.dp, 5.dp, 0.dp)
                                    .size(15.dp),
                            painter = languageArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onBackground,
                        )
                    }

                    if (showMoreLanguage) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset =
                                IntOffset(
                                    with(density) { ((20).dp).roundToPx() },
                                    with(density) { (30.dp).roundToPx() },
                                ),
                            onDismissRequest = {
                                if (showMoreLanguage) {
                                    showMoreLanguage = false
                                    languageOnDismissTime = Clock.System.now().toEpochMilliseconds()
                                }
                            },
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .wrapContentSize()
                                        .background(Color.Transparent)
                                        .shadow(15.dp),
                            ) {
                                val maxWidth =
                                    max(
                                        150.dp,
                                        getMenWidth(copywriter.getAllLanguages().map { it.name }.toTypedArray()),
                                    )

                                Column(
                                    modifier =
                                        Modifier
                                            .width(maxWidth)
                                            .wrapContentHeight()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colors.surface),
                                ) {
                                    val allLanguages = copywriter.getAllLanguages()
                                    allLanguages.forEachIndexed { _, language ->
                                        MenuItem(language.name) {
                                            copywriter.switchLanguage(language.abridge)
                                            showMoreLanguage = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(start = 35.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = palette(),
                        contentDescription = "theme",
                        tint = Color(0xFFFFC94A),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    settingsText(copywriter.getText("theme"))

                    Spacer(modifier = Modifier.weight(1f))

                    ThemeSegmentedControl()
                }

                Divider(modifier = Modifier.padding(start = 35.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = clipboard(),
                        contentDescription = "Enable Pasteboard Listening",
                        tint = Color(0xFF41B06E),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    settingsText(copywriter.getText("pasteboard_listening"))

                    Spacer(modifier = Modifier.weight(1f))

                    var enablePasteboardListening by remember { mutableStateOf(configManager.config.enablePasteboardListening) }
                    CustomSwitch(
                        modifier =
                            Modifier.width(32.dp)
                                .height(20.dp),
                        checked = enablePasteboardListening,
                        onCheckedChange = {
                            pasteboardService.toggle()
                            enablePasteboardListening = configManager.config.enablePasteboardListening
                        },
                    )
                }

                Divider(modifier = Modifier.padding(start = 35.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = shield(),
                        contentDescription = "Encrypted sync",
                        tint = Color(0xFF41C9E2),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    settingsText(copywriter.getText("encrypted_sync"))

                    Spacer(modifier = Modifier.weight(1f))

                    var isEncrypted by remember { mutableStateOf(configManager.config.isEncryptSync) }
                    CustomSwitch(
                        modifier =
                            Modifier.width(32.dp)
                                .height(20.dp),
                        checked = isEncrypted,
                        onCheckedChange = { changeIsEncryptSync ->
                            configManager.updateConfig("isEncryptSync", changeIsEncryptSync)
                            isEncrypted = configManager.config.isEncryptSync
                        },
                    )
                }

                Divider(modifier = Modifier.padding(start = 35.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = bolt(),
                        contentDescription = "Boot start up",
                        tint = Color(0xFF90D26D),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    settingsText(copywriter.getText("boot_start_up"))

                    Spacer(modifier = Modifier.weight(1f))

                    var enableAutoStartUp by remember { mutableStateOf(configManager.config.enableAutoStartUp) }

                    CustomSwitch(
                        modifier =
                            Modifier.width(32.dp)
                                .height(20.dp),
                        checked = enableAutoStartUp,
                        onCheckedChange = { changeEnableAutoStartUp ->
                            configManager.updateConfig("enableAutoStartUp", changeEnableAutoStartUp)
                            enableAutoStartUp = configManager.config.enableAutoStartUp
                        },
                    )
                }

                Divider(modifier = Modifier.padding(start = 35.dp))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(15.dp),
                        painter = rocket(),
                        contentDescription = "Automatic Update",
                        tint = Color(0xFFFB6D48),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    settingsText(copywriter.getText("automatic_update"))

                    Spacer(modifier = Modifier.weight(1f))

                    var isChecked by remember { mutableStateOf(false) }
                    CustomSwitch(
                        modifier =
                            Modifier.width(32.dp)
                                .height(20.dp),
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ExpandView("network") {
                NetSettingsView()
            }
            Spacer(modifier = Modifier.height(10.dp))
            ExpandView("store") {
                StoreSettingsView()
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        VerticalScrollbar(
            modifier =
                Modifier.background(color = Color.Transparent)
                    .fillMaxHeight().align(Alignment.CenterEnd)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state =
                            rememberDraggableState { delta ->
                                coroutineScope.launch(CoroutineName("ScrollPaste")) {
                                    scrollState.scrollBy(-delta)
                                }
                            },
                    ),
            adapter = rememberScrollbarAdapter(scrollState),
            style =
                ScrollbarStyle(
                    minimalHeight = 16.dp,
                    thickness = 8.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 300,
                    unhoverColor = if (isScrolling) MaterialTheme.colors.onBackground.copy(alpha = 0.48f) else Color.Transparent,
                    hoverColor = MaterialTheme.colors.onBackground,
                ),
        )
    }
}

@Composable
fun settingsText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = text,
        color = MaterialTheme.colors.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style =
            TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
            ),
    )
}
