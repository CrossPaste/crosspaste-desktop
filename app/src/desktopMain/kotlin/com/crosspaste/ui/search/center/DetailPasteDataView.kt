package com.crosspaste.ui.search.center

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.PasteType
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.paste.detail.ColorDetailView
import com.crosspaste.ui.paste.detail.FilesDetailView
import com.crosspaste.ui.paste.detail.HtmlDetailView
import com.crosspaste.ui.paste.detail.ImagesDetailView
import com.crosspaste.ui.paste.detail.RtfDetailView
import com.crosspaste.ui.paste.detail.TextDetailView
import com.crosspaste.ui.paste.detail.UrlDetailView
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
                    scope.TextDetailView(onDoubleClick)
                }
                PasteType.COLOR_TYPE -> {
                    scope.ColorDetailView(onDoubleClick)
                }
                PasteType.URL_TYPE -> {
                    scope.UrlDetailView(onDoubleClick)
                }
                PasteType.HTML_TYPE -> {
                    scope.HtmlDetailView(onDoubleClick)
                }
                PasteType.RTF_TYPE -> {
                    scope.RtfDetailView(onDoubleClick)
                }
                PasteType.IMAGE_TYPE -> {
                    scope.ImagesDetailView(onDoubleClick)
                }
                PasteType.FILE_TYPE -> {
                    scope.FilesDetailView(onDoubleClick)
                }
                else -> {
                }
            }
        }
    }
}
