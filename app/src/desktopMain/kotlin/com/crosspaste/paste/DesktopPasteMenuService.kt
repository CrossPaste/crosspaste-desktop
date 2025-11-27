package com.crosspaste.paste

import androidx.compose.foundation.ContextMenuItem
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.OCRModule
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem.Companion.createTextPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPasteMenuService(
    appWindowManager: AppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
    private val ocrModule: OCRModule,
    private val uiSupport: UISupport,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteMenuService {
    private val desktopAppWindowManager = appWindowManager as DesktopAppWindowManager

    private val menuScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private fun copySingleFile(
        id: Long,
        pasteItem: PasteItem,
    ) {
        menuScope.launch {
            pasteboardService
                .tryWritePasteboard(
                    id = id,
                    pasteItem = pasteItem,
                    localOnly = true,
                ).onSuccess {
                    notificationManager.sendNotification(
                        title = { it.getText("copy_successful") },
                        messageType = MessageType.Success,
                    )
                }.onFailure { e ->
                    notificationManager.sendNotification(
                        title = { it.getText("copy_failed") },
                        message = e.message?.let { message -> { message } },
                        messageType = MessageType.Error,
                    )
                }
        }
    }

    override fun copyPasteData(pasteData: PasteData) {
        menuScope.launch {
            pasteboardService
                .tryWritePasteboard(
                    pasteData = pasteData,
                    localOnly = true,
                ).onSuccess {
                    notificationManager.sendNotification(
                        title = { it.getText("copy_successful") },
                        messageType = MessageType.Success,
                    )
                }.onFailure { e ->
                    notificationManager.sendNotification(
                        title = { it.getText("copy_failed") },
                        message = e.message?.let { message -> { message } },
                        messageType = MessageType.Error,
                    )
                }
        }
    }

    override fun openPasteData(
        pasteData: PasteData,
        index: Int,
    ) {
        menuScope.launch {
            uiSupport.openPasteData(pasteData, index)
            val pasteType = pasteData.getType()
            if (!pasteType.isText() && !pasteType.isColor()) {
                desktopAppWindowManager.hideMainWindow()
            } else {
                desktopAppWindowManager.showMainWindow()
            }
        }
    }

    override fun deletePasteData(pasteData: PasteData) {
        menuScope.launch {
            pasteDao.markDeletePasteData(pasteData.id)
        }
    }

    override fun quickPasteFromMainWindow(pasteData: PasteData) {
        menuScope.launch {
            desktopAppWindowManager.hideMainWindowAndPaste {
                withContext(menuScope.coroutineContext) {
                    pasteboardService
                        .tryWritePasteboard(
                            pasteData = pasteData,
                            localOnly = true,
                            updateCreateTime = true,
                        ).isSuccess
                }
            }
        }
    }

    override fun quickPasteFromSearchWindow(pasteData: PasteData) {
        menuScope.launch {
            desktopAppWindowManager.hideSearchWindowAndPaste(0) {
                withContext(menuScope.coroutineContext) {
                    pasteboardService
                        .tryWritePasteboard(
                            pasteData = pasteData,
                            localOnly = true,
                            updateCreateTime = true,
                        ).isSuccess
                }
            }
        }
    }

    fun fileMenuItemsProvider(
        pasteData: PasteData,
        pasteItem: PasteItem,
        index: Int = 0,
    ): () -> List<ContextMenuItem> =
        {
            listOf(
                ContextMenuItem(copywriter.getText("copy")) {
                    copySingleFile(
                        id = pasteData.id,
                        pasteItem = pasteItem,
                    )
                },
                ContextMenuItem(copywriter.getText("open")) {
                    openPasteData(pasteData, index)
                },
            )
        }

    private fun createCopyContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("copy")) {
            copyPasteData(pasteData)
        }

    private fun createDeleteContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("delete")) {
            deletePasteData(pasteData)
        }

    private fun createEditContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("edit")) {
            openPasteData(pasteData)
        }

    private fun createExtractTextContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("extract_text")) {
            menuScope.launch {
                val extractText =
                    pasteData
                        .getPasteItem(ImagesPasteItem::class)
                        ?.getFilePaths(userDataPathProvider)
                        ?.mapNotNull { path ->
                            ocrModule.extractText(path).getOrNull()
                        }?.joinToString(separator = "\n")

                if (!extractText.isNullOrEmpty()) {
                    pasteboardService.tryWritePasteboard(
                        pasteItem = createTextPasteItem(text = extractText),
                        localOnly = false,
                    )
                }
            }
        }

    private fun createOpenContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("open")) {
            openPasteData(pasteData)
        }

    private fun createLoadingMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createDeleteContextMenuItem(pasteData),
        )

    private fun createBaseMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createOpenContextMenuItem(pasteData),
            createDeleteContextMenuItem(pasteData),
        )

    private fun createTextMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createEditContextMenuItem(pasteData),
            createDeleteContextMenuItem(pasteData),
        )

    private fun createImageMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createEditContextMenuItem(pasteData),
            createExtractTextContextMenuItem(pasteData),
            createDeleteContextMenuItem(pasteData),
        )

    fun mainPasteMenuItemsProvider(pasteData: PasteData): () -> List<ContextMenuItem> =
        {
            if (pasteData.pasteState == PasteState.LOADING) {
                createLoadingMenuItems(pasteData)
            } else {
                when (pasteData.getType()) {
                    PasteType.TEXT_TYPE -> createTextMenuItems(pasteData)
                    PasteType.COLOR_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.URL_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.HTML_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.RTF_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.IMAGE_TYPE -> createImageMenuItems(pasteData)
                    PasteType.FILE_TYPE -> createBaseMenuItems(pasteData)
                    else -> createLoadingMenuItems(pasteData)
                }
            }
        }

    fun sidePasteMenuItemsProvider(pasteDataScope: PasteDataScope): () -> List<ContextMenuItem> =
        {
            val pasteData = pasteDataScope.pasteData
            if (pasteData.pasteState == PasteState.LOADING) {
                createLoadingMenuItems(pasteData)
            } else {
                when (pasteData.getType()) {
                    PasteType.TEXT_TYPE -> createTextMenuItems(pasteData)
                    PasteType.COLOR_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.URL_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.HTML_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.RTF_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.IMAGE_TYPE -> createImageMenuItems(pasteData)
                    PasteType.FILE_TYPE -> createBaseMenuItems(pasteData)
                    else -> createLoadingMenuItems(pasteData)
                }
            }
        }
}
