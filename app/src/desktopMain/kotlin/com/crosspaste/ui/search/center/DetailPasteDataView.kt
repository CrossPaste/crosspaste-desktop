package com.crosspaste.ui.search.center

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.PasteType
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.paste.detail.HtmlToImageDetailView
import com.crosspaste.ui.paste.detail.PasteColorDetailView
import com.crosspaste.ui.paste.detail.PasteFilesDetailView
import com.crosspaste.ui.paste.detail.PasteImagesDetailView
import com.crosspaste.ui.paste.detail.PasteTextDetailView
import com.crosspaste.ui.paste.detail.PasteUrlDetailView
import com.crosspaste.ui.paste.detail.RtfToImageDetailView
import org.koin.compose.koinInject

@Composable
fun DetailPasteDataView() {
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val currentPasteData by pasteSelectionViewModel.currentPasteData.collectAsState()

    currentPasteData?.let { pasteData ->

        val scope =
            remember(pasteData.id, pasteData.pasteState) {
                createPasteDataScope(pasteData)
            }

        scope?.let {
            val pasteMenuService = koinInject<DesktopPasteMenuService>()
            val onDoubleClick: () -> Unit = {
                pasteMenuService.quickPasteFromSearchWindow(pasteData)
            }

            when (pasteData.getType()) {
                PasteType.TEXT_TYPE -> {
                    scope.PasteTextDetailView(onDoubleClick)
                }
                PasteType.COLOR_TYPE -> {
                    scope.PasteColorDetailView(onDoubleClick)
                }
                PasteType.URL_TYPE -> {
                    scope.PasteUrlDetailView(onDoubleClick)
                }
                PasteType.HTML_TYPE -> {
                    scope.HtmlToImageDetailView(onDoubleClick)
                }
                PasteType.RTF_TYPE -> {
                    scope.RtfToImageDetailView(onDoubleClick)
                }
                PasteType.IMAGE_TYPE -> {
                    scope.PasteImagesDetailView(onDoubleClick)
                }
                PasteType.FILE_TYPE -> {
                    scope.PasteFilesDetailView(onDoubleClick)
                }
                else -> {
                }
            }
        }
    }
}
