package com.crosspaste.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.LocalKoinApplication
import com.crosspaste.clip.ClipSearchService
import com.crosspaste.clip.item.ClipFiles
import com.crosspaste.clip.item.ClipHtml
import com.crosspaste.clip.item.ClipText
import com.crosspaste.clip.item.ClipUrl
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.dao.clip.ClipType
import com.crosspaste.ui.clip.detail.ClipFilesDetailView
import com.crosspaste.ui.clip.detail.ClipImagesDetailView
import com.crosspaste.ui.clip.detail.ClipTextDetailView
import com.crosspaste.ui.clip.detail.ClipUrlDetailView
import com.crosspaste.ui.clip.detail.HtmlToImageDetailView
import com.crosspaste.ui.clip.preview.getClipItem

@Composable
fun DetialClipDataView() {
    val current = LocalKoinApplication.current
    val clipSearchService = current.koin.get<ClipSearchService>()

    val currentClipData: ClipData? by remember(
        clipSearchService.searchTime,
        clipSearchService.selectedIndex,
    ) { clipSearchService.currentClipData }

    currentClipData?.let { clipData ->
        clipData.getClipItem()?.let {
            when (clipData.clipType) {
                ClipType.TEXT -> {
                    ClipTextDetailView(clipData, it as ClipText)
                }
                ClipType.URL -> {
                    ClipUrlDetailView(clipData, it as ClipUrl)
                }
                ClipType.HTML -> {
                    HtmlToImageDetailView(clipData, it as ClipHtml)
                }
                ClipType.IMAGE -> {
                    ClipImagesDetailView(clipData, it as ClipFiles)
                }
                ClipType.FILE -> {
                    ClipFilesDetailView(clipData, it as ClipFiles)
                }
                else -> {
                }
            }
        }
    } ?: run {
        Spacer(modifier = Modifier.fillMaxSize())
    }
}
