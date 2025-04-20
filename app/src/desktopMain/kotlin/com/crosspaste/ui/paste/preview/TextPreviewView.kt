package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteText
import com.crosspaste.ui.paste.PasteboardViewProvider.Companion.previewTextStyle

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
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    style =
                        previewTextStyle.copy(
                            lineHeightStyle =
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None,
                                ),
                        ),
                )
            }
        }
    }
}
