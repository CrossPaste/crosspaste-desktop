package com.clipevery.ui.clip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipevery.clip.item.ClipHtml
import com.clipevery.dao.clip.ClipData
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData

@Composable
fun HtmlPreviewView(clipData: ClipData) {

    clipData.getClipItem(ClipHtml::class)?.let {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp)
        ) {
            val webViewState = rememberWebViewStateWithHTMLData(
                data = it.html
            )
            WebView(
                modifier = Modifier.fillMaxSize(),
                state = webViewState
            )
        }

    }
}
