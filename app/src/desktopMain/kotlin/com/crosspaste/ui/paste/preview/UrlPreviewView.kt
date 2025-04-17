package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.paste.PasteboardViewProvider.Companion.previewUrlStyle

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
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = previewUrlStyle,
                )
            }
        }
    }
}
