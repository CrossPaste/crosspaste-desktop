package com.clipevery.ui.clip.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipUrl
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.link
import java.awt.Desktop
import java.net.URI

@Composable
fun UrlPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    link(),
                    contentDescription = "Link",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground,
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = copywriter.getText("Link"),
                    fontFamily = FontFamily.SansSerif,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp,
                        ),
                )
            }
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
