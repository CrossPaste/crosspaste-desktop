package com.crosspaste.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.clip.item.ClipText
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.ui.base.UISupport

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val current = LocalKoinApplication.current
        val uiSupport = current.koin.get<UISupport>()

        ClipSpecificPreviewContentView(
            clipMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize().onClick {
                            uiSupport.openText((it as ClipText).text)
                        }.padding(10.dp),
                ) {
                    Text(
                        modifier = Modifier.fillMaxSize(),
                        text = (it as ClipText).text,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colors.onBackground,
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
