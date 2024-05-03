package com.clipevery.ui.clip.preview

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
import com.clipevery.clip.item.ClipText
import com.clipevery.dao.clip.ClipData
import com.clipevery.utils.getFileUtils
import java.awt.Desktop

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val fileUtils = getFileUtils()
        ClipSpecificPreviewContentView({
            Row(
                modifier =
                    Modifier.fillMaxSize().onClick {
                        if (Desktop.isDesktopSupported()) {
                            val desktop = Desktop.getDesktop()
                            fileUtils.createTempFile(
                                (it as ClipText).text.toByteArray(),
                                fileUtils.createRandomFileName("txt"),
                            )?.let {
                                    path ->
                                desktop.open(path.toFile())
                            }
                        }
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
        }, {
            ClipMenuView(clipData = clipData)
        })
    }
}
