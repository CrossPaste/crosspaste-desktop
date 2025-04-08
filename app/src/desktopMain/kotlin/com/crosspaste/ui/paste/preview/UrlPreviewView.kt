package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteUrl

@Composable
fun UrlPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteUrl::class)?.let { pasteUrl ->
        SimplePreviewContentView(pasteData) {
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(10.dp),
            ) {
                Text(
                    modifier =
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    text = pasteUrl.url,
                    textDecoration = TextDecoration.Underline,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                )
            }
        }
    }
}
