package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.theme.AppUIFont.pasteUrlStyle
import com.crosspaste.ui.theme.AppUIFont.previewAutoSize
import com.crosspaste.ui.theme.AppUISize.small3X

@Composable
fun UrlPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteUrl::class)?.let { pasteUrl ->
        SimplePreviewContentView(pasteData) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(small3X),
            ) {
                BasicText(
                    modifier =
                        Modifier.fillMaxSize(),
                    text = pasteUrl.url,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = pasteUrlStyle,
                    autoSize = previewAutoSize,
                )
            }
        }
    }
}
