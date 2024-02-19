package com.clipevery.ui.clip

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.clipevery.clip.item.ClipText
import com.clipevery.dao.clip.ClipData

fun getTitle(clipText: ClipText): String {
    return clipText.text
}

@Composable
fun TextPreviewView(clipData: ClipData) {
    clipData.getClipItem(ClipText::class)?.let {
        Column() {
            Text(text = getTitle(it),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 17.sp
                )
            )
        }
    }

}