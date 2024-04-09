package com.clipevery.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppEnv
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.clip.ClipPreviewsView
import com.clipevery.ui.devices.DevicesView
import com.clipevery.ui.devices.bindingQRCode

@Composable
fun TabsView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val appEnv = current.koin.get<AppEnv>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    val tabs =
        remember {
            listOfNotNull(
                Pair(listOf(PageViewType.CLIP_PREVIEW), "Clipboard"),
                Pair(listOf(PageViewType.MY_DEVICES, PageViewType.NEARBY_DEVICES), "Devices"),
                Pair(listOf(PageViewType.QR_CODE), "Scan"),
                if (appEnv == AppEnv.DEVELOPMENT) Pair(listOf(PageViewType.DEBUG), "Debug") else null,
            )
        }
    Row(
        modifier =
            Modifier.padding(8.dp)
                .wrapContentWidth(),
    ) {
        tabs.forEach { pair ->
            TabView(currentPageViewContext, pair.first, copywriter.getText(pair.second))
        }

        Spacer(modifier = Modifier.fillMaxWidth())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        when (currentPageViewContext.value.pageViewType) {
            PageViewType.CLIP_PREVIEW -> ClipPreviewsView()
            PageViewType.MY_DEVICES,
            PageViewType.NEARBY_DEVICES,
            -> DevicesView(currentPageViewContext)
            PageViewType.QR_CODE -> bindingQRCode()
            PageViewType.DEBUG -> DebugView()
            else -> ClipPreviewsView()
        }
    }
}

val bottomBorderShape =
    GenericShape { size, _ ->
        moveTo(0f, size.height)
        lineTo(size.width, size.height)
        lineTo(size.width, size.height - 6)
        lineTo(0f, size.height - 6)
        close()
    }

@Composable
fun TabView(
    currentPageViewContext: MutableState<PageViewContext>,
    pageViewTypes: List<PageViewType>,
    title: String,
) {
    val textStyle: TextStyle
    val textUnit: TextUnit
    var modifier: Modifier =
        Modifier.padding(2.dp)
            .height(30.dp)
            .wrapContentSize(Alignment.CenterStart)

    if (pageViewTypes.contains(currentPageViewContext.value.pageViewType)) {
        textStyle = TextStyle(fontWeight = FontWeight.Bold)
        modifier = modifier.border(5.dp, MaterialTheme.colors.primary, bottomBorderShape)
        textUnit = 16.sp
    } else {
        textStyle = TextStyle(fontWeight = FontWeight.Normal)
        textUnit = 12.sp
    }

    Box(modifier = modifier) {
        Text(
            text = title,
            color = MaterialTheme.colors.onBackground,
            fontSize = textUnit,
            style = textStyle,
            fontFamily = FontFamily.SansSerif,
            modifier =
                Modifier
                    .padding(8.dp, 0.dp, 8.dp, 8.dp)
                    .align(Alignment.BottomStart)
                    .clickable { currentPageViewContext.value = PageViewContext(pageViewTypes[0]) },
        )
    }
}
