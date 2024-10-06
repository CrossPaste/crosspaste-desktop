package com.crosspaste.ui.paste.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PasteSpecificPreviewView(pasteData: PasteData) {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView(pasteData)
    } else {
        val appWindowManager = koinInject<AppWindowManager>() as DesktopAppWindowManager
        val pasteboardService = koinInject<PasteboardService>()
        val scope = rememberCoroutineScope()
        val onDoubleClick: () -> Unit = {
            scope.launch {
                appWindowManager.unActiveMainWindow {
                    withContext(ioDispatcher) {
                        pasteboardService.tryWritePasteboard(
                            pasteData = pasteData,
                            localOnly = true,
                            updateCreateTime = true,
                        )
                        true
                    }
                }
            }
        }
        when (pasteData.pasteType) {
            PasteType.TEXT -> TextPreviewView(pasteData, onDoubleClick)
            PasteType.URL -> UrlPreviewView(pasteData, onDoubleClick)
            PasteType.HTML -> HtmlToImagePreviewView(pasteData, onDoubleClick)
            PasteType.IMAGE -> ImagesPreviewView(pasteData, onDoubleClick)
            PasteType.FILE -> FilesPreviewView(pasteData, onDoubleClick)
        }
    }
}
