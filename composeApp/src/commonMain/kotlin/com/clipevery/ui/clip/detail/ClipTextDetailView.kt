package com.clipevery.ui.clip.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.clipevery.clip.item.ClipText

@Composable
fun ClipTextDetailView(clipText: ClipText) {
    val text = clipText.text
    Text(
        text = text,
        modifier = Modifier.fillMaxSize(),
        overflow = TextOverflow.Ellipsis,
        style =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colors.onBackground,
                fontSize = 14.sp,
            ),
    )
}
