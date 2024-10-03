package com.crosspaste.ui.paste.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteboardService
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PasteSpecificPreviewView(pasteData: PasteData) {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView(pasteData)
    } else {
        val appWindowManager = koinInject<AppWindowManager>()
        val copywriter = koinInject<GlobalCopywriter>()
        val pasteboardService = koinInject<PasteboardService>()
        val notificationManager = koinInject<NotificationManager>()
        val scope = rememberCoroutineScope()
        val onDoubleClick: () -> Unit = {
            appWindowManager.setMainCursorWait()
            scope.launch(ioDispatcher) {
                pasteboardService.tryWritePasteboard(
                    pasteData,
                    localOnly = true,
                    filterFile = false,
                )
                withContext(mainDispatcher) {
                    appWindowManager.resetMainCursor()
                    notificationManager.addNotification(
                        message = copywriter.getText("copy_successful"),
                        messageType = MessageType.Success,
                    )
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
