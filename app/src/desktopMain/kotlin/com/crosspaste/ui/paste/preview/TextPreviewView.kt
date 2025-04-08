package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteText

@Composable
fun TextPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteText::class)?.let { pasteText ->
        SimplePreviewContentView(pasteData) {
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(10.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxSize(),
                    text = pasteText.previewText(),
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                )
            }
        }
    }
}
