package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteText
import com.crosspaste.ui.theme.AppUIFont.pasteTextStyle
import com.crosspaste.ui.theme.AppUIFont.previewAutoSize
import com.crosspaste.ui.theme.AppUISize.small3X

@Composable
fun TextPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteText::class)?.let { pasteText ->
        SimplePreviewContentView(pasteData) {
            Row(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(small3X),
            ) {
                BasicText(
                    modifier = Modifier.fillMaxSize(),
                    text = AnnotatedString(pasteText.previewText()),
                    maxLines = 4,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    style = pasteTextStyle,
                    autoSize = previewAutoSize,
                )
            }
        }
    }
}
