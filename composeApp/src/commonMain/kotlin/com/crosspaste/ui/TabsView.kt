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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppEnv
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.clip.preview.ClipPreviewsView
import com.crosspaste.ui.devices.DevicesView
import com.crosspaste.ui.devices.bindingQRCode

val tabTextStyle =
    TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
    )

@Composable
fun TabsView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val appEnv = current.koin.get<AppEnv>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    val textMeasurer = rememberTextMeasurer()

    val tabs =
        remember {
            listOfNotNull(
                Pair(listOf(PageViewType.CLIP_PREVIEW), "Clipboard"),
                Pair(listOf(PageViewType.DEVICES), "Devices"),
                Pair(listOf(PageViewType.QR_CODE), "Scan"),
                if (appEnv == AppEnv.DEVELOPMENT) Pair(listOf(PageViewType.DEBUG), "Debug") else null,
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp)
                    .wrapContentWidth(),
        ) {
            tabs.forEach { pair ->
                TabView(currentPageViewContext, pair.first, copywriter.getText(pair.second))
            }
            Spacer(modifier = Modifier.fillMaxWidth())
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

        val selectedIndexTransition = updateTransition(targetState = selectedIndex, label = "selectedIndexTransition")
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
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(modifier = Modifier.width(4.dp + (10.dp * ((selectedIndex * 2) + 1)) + offset))

            Box(
                modifier =
                    Modifier
                        .width(width)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colors.primary),
            )
        }

        Divider(modifier = Modifier.fillMaxWidth())

        when (currentPageViewContext.value.pageViewType) {
            PageViewType.CLIP_PREVIEW -> ClipPreviewsView()
            PageViewType.DEVICES -> DevicesView(currentPageViewContext)
            PageViewType.QR_CODE -> bindingQRCode()
            PageViewType.DEBUG -> DebugView()
            else -> ClipPreviewsView()
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
    ) {
        Row(
            modifier =
                Modifier.wrapContentSize()
                    .padding(horizontal = 5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (hover) MaterialTheme.colors.selectColor() else MaterialTheme.colors.background)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colors.onBackground,
                style = tabTextStyle,
            )
        }
    }
}
