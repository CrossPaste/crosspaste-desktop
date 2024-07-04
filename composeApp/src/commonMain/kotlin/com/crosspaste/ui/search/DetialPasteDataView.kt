package com.crosspaste.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.paste.detail.HtmlToImageDetailView
import com.crosspaste.ui.paste.detail.PasteFilesDetailView
import com.crosspaste.ui.paste.detail.PasteImagesDetailView
import com.crosspaste.ui.paste.detail.PasteTextDetailView
import com.crosspaste.ui.paste.detail.PasteUrlDetailView
import com.crosspaste.ui.paste.preview.getPasteItem

@Composable
fun DetialPasteDataView() {
    val current = LocalKoinApplication.current
    val pasteSearchService = current.koin.get<PasteSearchService>()

    val currentPasteData: PasteData? by remember(
        pasteSearchService.searchTime,
        pasteSearchService.selectedIndex,
    ) { pasteSearchService.currentPasteData }

    currentPasteData?.let { pasteData ->
        pasteData.getPasteItem()?.let {
            when (pasteData.pasteType) {
                PasteType.TEXT -> {
                    PasteTextDetailView(pasteData, it as PasteText)
                }
                PasteType.URL -> {
                    PasteUrlDetailView(pasteData, it as PasteUrl)
                }
                PasteType.HTML -> {
                    HtmlToImageDetailView(pasteData, it as PasteHtml)
                }
                PasteType.IMAGE -> {
                    PasteImagesDetailView(pasteData, it as PasteFiles)
                }
                PasteType.FILE -> {
                    PasteFilesDetailView(pasteData, it as PasteFiles)
                }
                else -> {
                }
            }
        }
    } ?: run {
        Spacer(modifier = Modifier.fillMaxSize())
    }
}
