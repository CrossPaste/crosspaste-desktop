package com.crosspaste.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.paste.detail.HtmlToImageDetailView
import com.crosspaste.ui.paste.detail.PasteFilesDetailView
import com.crosspaste.ui.paste.detail.PasteImagesDetailView
import com.crosspaste.ui.paste.detail.PasteTextDetailView
import com.crosspaste.ui.paste.detail.PasteUrlDetailView
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetialPasteDataView() {
    val current = LocalKoinApplication.current
    val pasteSearchService = current.koin.get<PasteSearchService>()

    val currentPasteData: PasteData? by remember(
        pasteSearchService.searchTime,
        pasteSearchService.selectedIndex,
    ) { mutableStateOf(pasteSearchService.currentPasteData) }

    currentPasteData?.let { pasteData ->
        pasteData.getPasteItem()?.let {
            val appWindowManager = current.koin.get<AppWindowManager>()
            val copywriter = current.koin.get<GlobalCopywriter>()
            val pasteboardService = current.koin.get<PasteboardService>()
            val notificationManager = current.koin.get<NotificationManager>()
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
                            copywriter.getText("copy_successful"),
                            MessageType.Success,
                        )
                    }
                }
            }

            when (pasteData.pasteType) {
                PasteType.TEXT -> {
                    PasteTextDetailView(pasteData, it as PasteText, onDoubleClick)
                }
                PasteType.URL -> {
                    PasteUrlDetailView(pasteData, it as PasteUrl, onDoubleClick)
                }
                PasteType.HTML -> {
                    HtmlToImageDetailView(pasteData, it as PasteHtml, onDoubleClick)
                }
                PasteType.IMAGE -> {
                    PasteImagesDetailView(pasteData, it as PasteFiles, onDoubleClick)
                }
                PasteType.FILE -> {
                    PasteFilesDetailView(pasteData, it as PasteFiles, onDoubleClick)
                }
                else -> {
                }
            }
        }
    } ?: run {
        Spacer(modifier = Modifier.fillMaxSize())
    }
}
