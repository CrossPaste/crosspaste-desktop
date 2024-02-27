package com.clipevery.ui.clip

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipText
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.feed

@Composable
fun TextPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()
        ClipSpecificPreviewContentView(it, {
            Text(
                modifier = Modifier.fillMaxSize(),
                text = (it as ClipText).text,
                fontFamily = FontFamily.SansSerif,
                maxLines = 4,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 14.sp
                )
            )
        }, {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    feed(),
                    contentDescription = "Text",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = copywriter.getText("Text"),
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp
                    )
                )
            }
        })
    }
}