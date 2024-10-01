package com.crosspaste.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.trash
import com.crosspaste.ui.devices.DevicesScreen
import com.crosspaste.ui.devices.QRScreen
import com.crosspaste.ui.paste.PasteboardScreen
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

val tabTextStyle =
    TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
        lineHeight = 0.sp,
    )

@Composable
fun TabsView() {
    val currentScreenContext = LocalPageViewContent.current
    val appEnv = koinInject<AppEnv>()
    val copywriter = koinInject<GlobalCopywriter>()

    val textMeasurer = rememberTextMeasurer()

    val tabs =
        remember {
            listOfNotNull(
                Pair(listOf(ScreenType.PASTE_PREVIEW), "pasteboard"),
                Pair(listOf(ScreenType.DEVICES), "devices"),
                Pair(listOf(ScreenType.QR_CODE), "scan"),
                if (appEnv == AppEnv.DEVELOPMENT) Pair(listOf(ScreenType.DEBUG), "debug") else null,
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Box {
            Column(
                modifier =
                    Modifier.padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(0.64f)),
            ) {}

            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Row(
                    modifier =
                        Modifier.padding(12.dp, 0.dp, 15.dp, 0.dp)
                            .wrapContentWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { pair ->
                        TabView(currentScreenContext, pair.first, copywriter.getText(pair.second))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentScreenContext.value.screenType == ScreenType.PASTE_PREVIEW) {
                        val appWindowManager = koinInject<AppWindowManager>()
                        val notificationManager = koinInject<NotificationManager>()
                        val pasteRealm = koinInject<PasteRealm>()
                        val scope = rememberCoroutineScope()
                        PasteTooltipIconView(
                            painter = trash(),
                            text = copywriter.getText("clean_all_pasteboard"),
                            contentDescription = "clean all paste",
                        ) {
                            appWindowManager.setMainCursorWait()
                            scope.launch {
                                pasteRealm.markAllDeleteExceptFavorite()
                                withContext(mainDispatcher) {
                                    appWindowManager.resetMainCursor()
                                    notificationManager.addNotification(
                                        message = copywriter.getText("clean_successful"),
                                        messageType = MessageType.Success,
                                    )
                                }
                            }
                        }
                    }
                }

                val widthArray =
                    tabs.map {
                        textMeasurer.measure(
                            copywriter.getText(it.second),
                            tabTextStyle,
                        ).size.width
                    }

                val selectedIndex by remember(currentScreenContext.value.screenType) {
                    mutableStateOf(
                        tabs.indexOfFirst { it.first.contains(currentScreenContext.value.screenType) },
                    )
                }

                val selectedIndexTransition =
                    updateTransition(targetState = selectedIndex, label = "selectedIndexTransition")
                val width by selectedIndexTransition.animateDp(
                    transitionSpec = { tween(durationMillis = 250, easing = LinearEasing) },
                    label = "width",
                ) { tabIndex ->
                    with(LocalDensity.current) { widthArray[tabIndex].toDp() + 8.dp }
                }

                val offset by selectedIndexTransition.animateDp(
                    transitionSpec = { tween(durationMillis = 250, easing = LinearEasing) },
                    label = "offset",
                ) { tabIndex ->
                    var sum = 0.dp
                    for (i in 0 until tabIndex) {
                        sum += with(LocalDensity.current) { widthArray[i].toDp() }
                    }
                    sum
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(8.dp + (10.dp * ((selectedIndex * 2) + 1)) + offset))

                    Box(
                        modifier =
                            Modifier
                                .offset(y = (-2.5).dp)
                                .width(width)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        when (currentScreenContext.value.screenType) {
            ScreenType.PASTE_PREVIEW -> PasteboardScreen()
            ScreenType.DEVICES -> DevicesScreen()
            ScreenType.QR_CODE -> QRScreen()
            ScreenType.DEBUG -> DebugScreen()
            else -> PasteboardScreen()
        }
    }
}

@Composable
fun TabView(
    currentScreenContext: MutableState<ScreenContext>,
    screenTypes: List<ScreenType>,
    title: String,
) {
    SingleTabView(
        title,
    ) {
        currentScreenContext.value = ScreenContext(screenTypes[0])
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SingleTabView(
    title: String,
    onClick: () -> Unit,
) {
    var hover by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .height(30.dp)
                .wrapContentWidth()
                .onPointerEvent(
                    eventType = PointerEventType.Enter,
                    onEvent = {
                        hover = true
                    },
                )
                .onPointerEvent(
                    eventType = PointerEventType.Exit,
                    onEvent = {
                        hover = false
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onClick()
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.wrapContentSize()
                    .padding(horizontal = 5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = tabTextStyle,
            )
        }
    }
}
