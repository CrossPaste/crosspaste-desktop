package com.crosspaste.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import com.crosspaste.ui.devices.DevicesView
import com.crosspaste.ui.devices.bindingQRCode
import com.crosspaste.ui.paste.preview.PastePreviewsView
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
fun TabsView(currentPageViewContext: MutableState<PageViewContext>) {
    val appEnv = koinInject<AppEnv>()
    val copywriter = koinInject<GlobalCopywriter>()

    val textMeasurer = rememberTextMeasurer()

    val tabs =
        remember {
            listOfNotNull(
                Pair(listOf(PageViewType.PASTE_PREVIEW), "pasteboard"),
                Pair(listOf(PageViewType.DEVICES), "devices"),
                Pair(listOf(PageViewType.QR_CODE), "scan"),
                if (appEnv == AppEnv.DEVELOPMENT) Pair(listOf(PageViewType.DEBUG), "debug") else null,
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
                        .background(MaterialTheme.colors.surface.copy(0.64f)),
            ) {}

            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                Row(
                    modifier =
                        Modifier.padding(12.dp, 0.dp, 15.dp, 0.dp)
                            .wrapContentWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { pair ->
                        TabView(currentPageViewContext, pair.first, copywriter.getText(pair.second))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentPageViewContext.value.pageViewType == PageViewType.PASTE_PREVIEW) {
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

                val selectedIndex by remember(currentPageViewContext.value.pageViewType) {
                    mutableStateOf(
                        tabs.indexOfFirst { it.first.contains(currentPageViewContext.value.pageViewType) },
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
                                .background(MaterialTheme.colors.primary),
                    )
                }
            }
        }

        when (currentPageViewContext.value.pageViewType) {
            PageViewType.PASTE_PREVIEW -> PastePreviewsView()
            PageViewType.DEVICES -> DevicesView(currentPageViewContext)
            PageViewType.QR_CODE -> bindingQRCode()
            PageViewType.DEBUG -> DebugView()
            else -> PastePreviewsView()
        }
    }
}

@Composable
fun TabView(
    currentPageViewContext: MutableState<PageViewContext>,
    pageViewTypes: List<PageViewType>,
    title: String,
) {
    SingleTabView(
        title,
    ) {
        currentPageViewContext.value = PageViewContext(pageViewTypes[0])
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SingleTabView(
    title: String,
    clickable: () -> Unit,
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
                .onClick(onClick = { clickable() }),
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
                color = MaterialTheme.colors.onBackground,
                style = tabTextStyle,
            )
        }
    }
}
