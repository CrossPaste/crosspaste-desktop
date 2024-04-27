package com.clipevery.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.item.ClipHtml
import com.clipevery.clip.item.ClipText
import com.clipevery.ui.clip.detail.ClipDetailView
import com.clipevery.ui.clip.detail.ClipTextDetailView
import com.clipevery.ui.clip.detail.HtmlToImageDetailView
import com.clipevery.ui.clip.preview.getClipItem

@Composable
fun DetialClipDataView() {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()

    clipSearchService.currentClipData.value?.let { clipData ->
        clipData.getClipItem()?.let {
            ClipDetailView {
                when (it) {
                    is ClipText -> {
                        ClipTextDetailView(it)
                    }
                    is ClipHtml -> {
                        HtmlToImageDetailView(clipData, it)
                    }
                    else -> {
                    }
                }
            }
        }
    } ?: run {
        Spacer(modifier = Modifier.fillMaxSize())
    }
}
