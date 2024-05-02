package com.clipevery.ui.clip.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.clipevery.clip.item.ClipUrl
import com.clipevery.dao.clip.ClipData
import java.awt.Desktop
import java.net.URI

@Composable
fun UrlPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val clipUrl = it as ClipUrl
        ClipSpecificPreviewContentView(it, {
            Text(
                modifier =
                    Modifier.fillMaxSize()
                        .clickable {
                            openUrlInBrowser(clipUrl.url)
                        },
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
        }, {
            ClipMenuView(clipData = clipData)
        })
    }
}

fun openUrlInBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    } else {
        // todo show error message: cant open browser
    }
}
