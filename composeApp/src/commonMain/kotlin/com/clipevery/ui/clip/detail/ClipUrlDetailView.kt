package com.clipevery.ui.clip.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.clipevery.clip.item.ClipUrl
import com.clipevery.ui.clip.preview.openUrlInBrowser

@Composable
fun ClipUrlDetailView(clipUrl: ClipUrl) {
    val url = clipUrl.url
    Row(
        modifier =
            Modifier.fillMaxSize()
                .clickable {
                    openUrlInBrowser(clipUrl.url)
                }.padding(10.dp),
    ) {
        Text(
            text = url,
            modifier = Modifier.fillMaxSize(),
            overflow = TextOverflow.Ellipsis,
            style =
                TextStyle(
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colors.primary,
                    fontSize = 14.sp,
                ),
        )
    }
}
