package com.crosspaste.ui.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppWindowManager
import com.crosspaste.db.paste.PasteType
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.model.PasteSelectionViewModel
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
        pasteData.pasteAppearItem?.let {
            val appWindowManager = koinInject<AppWindowManager>()
            val pasteboardService = koinInject<PasteboardService>()
            val notificationManager = koinInject<NotificationManager>()
            val scope = rememberCoroutineScope()
            val onDoubleClick: () -> Unit = {
                appWindowManager.doLongTaskInMain(
                    scope = scope,
                    task = {
                        pasteboardService.tryWritePasteboard(
                            pasteData,
                            localOnly = true,
                            filterFile = false,
                        )
                    },
                    success = {
                        notificationManager.sendNotification(
                            title = { it.getText("copy_successful") },
                            messageType = MessageType.Success,
                        )
                    },
                    fail = {
                        notificationManager.sendNotification(
                            title = { it.getText("copy_failed") },
                            message = it.message?.let { message -> { it -> message } },
                            messageType = MessageType.Error,
                        )
                    },
                )
            }

            when (pasteData.getType()) {
                PasteType.TEXT_TYPE -> {
                    PasteTextDetailView(pasteData, it as PasteText, onDoubleClick)
                }
                PasteType.COLOR_TYPE -> {
                    PasteColorDetailView(pasteData, it as PasteColor, onDoubleClick)
                }
                PasteType.URL_TYPE -> {
                    PasteUrlDetailView(pasteData, it as PasteUrl, onDoubleClick)
                }
                PasteType.HTML_TYPE -> {
                    HtmlToImageDetailView(pasteData, it as PasteHtml, onDoubleClick)
                }
                PasteType.RTF_TYPE -> {
                    RtfToImageDetailView(pasteData, it as PasteRtf, onDoubleClick)
                }
                PasteType.IMAGE_TYPE -> {
                    PasteImagesDetailView(pasteData, it as PasteFiles, onDoubleClick)
                }
                PasteType.FILE_TYPE -> {
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
