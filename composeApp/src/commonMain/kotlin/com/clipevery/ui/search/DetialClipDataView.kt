package com.clipevery.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.item.ClipFiles
import com.clipevery.clip.item.ClipHtml
import com.clipevery.clip.item.ClipText
import com.clipevery.clip.item.ClipUrl
import com.clipevery.dao.clip.ClipType
import com.clipevery.ui.clip.detail.ClipDetailView
import com.clipevery.ui.clip.detail.ClipImagesDetailView
import com.clipevery.ui.clip.detail.ClipTextDetailView
import com.clipevery.ui.clip.detail.ClipUrlDetailView
import com.clipevery.ui.clip.detail.HtmlToImageDetailView
import com.clipevery.ui.clip.preview.getClipItem

@Composable
fun DetialClipDataView() {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()

    clipSearchService.currentClipData.value?.let { clipData ->
        clipData.getClipItem()?.let {
            ClipDetailView {
                when (clipData.clipType) {
                    ClipType.TEXT -> {
                        ClipTextDetailView(it as ClipText)
                    }
                    ClipType.URL -> {
                        ClipUrlDetailView(it as ClipUrl)
                    }
                    ClipType.HTML -> {
                        HtmlToImageDetailView(clipData, it as ClipHtml)
                    }
                    ClipType.IMAGE -> {
                        ClipImagesDetailView(it as ClipFiles)
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
