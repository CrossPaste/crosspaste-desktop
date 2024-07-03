package com.crosspaste.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.clip.item.ClipUrl
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.ui.base.UISupport

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrlPreviewView(clipData: ClipData) {
    val current = LocalKoinApplication.current
    val uiSupport = current.koin.get<UISupport>()

    clipData.getClipItem()?.let {
        val clipUrl = it as ClipUrl
        ClipSpecificPreviewContentView(
            clipMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .onClick {
                                uiSupport.openUrlInBrowser(clipUrl.url)
                            }.padding(10.dp),
                ) {
                    Text(
                        modifier =
                            Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        text = clipUrl.url,
                        textDecoration = TextDecoration.Underline,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colors.primary,
                                fontSize = 14.sp,
                            ),
                    )
                }
            },
            clipRightInfo = { toShow ->
                ClipMenuView(clipData = clipData, toShow = toShow)
            },
        )
    }
}
