package com.crosspaste.paste

import androidx.compose.foundation.ContextMenuItem
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPasteMenuService(
    private val appWindowManager: AppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
    private val uiSupport: UISupport,
) : PasteMenuService {
    private val desktopAppWindowManager = appWindowManager as DesktopAppWindowManager

    private val menuScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private fun copySingleFile(
        id: Long,
        pasteItem: PasteItem,
    ) {
        appWindowManager.doLongTaskInMain(
            scope = menuScope,
            task = {
                pasteboardService.tryWritePasteboard(
                    id = id,
                    pasteItem = pasteItem,
                    localOnly = true,
                )
            },
            success = {
                notificationManager.sendNotification(
                    title = { it.getText("copy_successful") },
                    messageType = MessageType.Success,
                )
            },
            fail = { e ->
                notificationManager.sendNotification(
                    title = { it.getText("copy_failed") },
                    message = e.message?.let { message -> { message } },
                    messageType = MessageType.Error,
                )
            },
        )
    }

    override fun copyPasteData(pasteData: PasteData) {
        appWindowManager.doLongTaskInMain(
            scope = menuScope,
            task = {
                pasteboardService.tryWritePasteboard(
                    pasteData = pasteData,
                    localOnly = true,
                )
            },
            success = {
                notificationManager.sendNotification(
                    title = { it.getText("copy_successful") },
                    messageType = MessageType.Success,
                )
            },
            fail = { e ->
                notificationManager.sendNotification(
                    title = { it.getText("copy_failed") },
                    message = e.message?.let { message -> { message } },
                    messageType = MessageType.Error,
                )
            },
        )
    }

    override fun openPasteData(
        pasteData: PasteData,
        index: Int,
    ) {
        uiSupport.openPasteData(pasteData, index)
        val pasteType = pasteData.getType()
        if (!pasteType.isText() && !pasteType.isColor()) {
            desktopAppWindowManager.hideMainWindow()
        } else {
            desktopAppWindowManager.showMainWindow()
        }
    }

    override fun deletePasteData(pasteData: PasteData) {
        appWindowManager.doLongTaskInMain(
            scope = menuScope,
            task = {
                pasteDao.markDeletePasteData(pasteData.id)
            },
        )
    }

    override fun quickPasteFromMainWindow(pasteData: PasteData) {
        menuScope.launch {
            desktopAppWindowManager.hideMainWindowAndPaste {
                withContext(ioDispatcher) {
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
            desktopAppWindowManager.hideSearchWindowAndPaste {
                withContext(ioDispatcher) {
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

    private fun createLoadingMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createDeleteContextMenuItem(pasteData),
        )

    private fun createBaseMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            ContextMenuItem(copywriter.getText("open")) {
                openPasteData(pasteData)
            },
            createDeleteContextMenuItem(pasteData),
        )

    private fun createTextMenuItems(pasteDataScope: PasteDataScope): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteDataScope.pasteData),
            ContextMenuItem(copywriter.getText("edit")) {
                openPasteData(pasteDataScope.pasteData)
            },
            createDeleteContextMenuItem(pasteDataScope.pasteData),
        )

    fun mainPasteMenuItemsProvider(pasteData: PasteData): () -> List<ContextMenuItem> =
        {
            if (pasteData.pasteState == PasteState.LOADING) {
                createLoadingMenuItems(pasteData)
            } else {
                when (pasteData.getType()) {
                    PasteType.TEXT_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.COLOR_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.URL_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.HTML_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.RTF_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.IMAGE_TYPE -> createBaseMenuItems(pasteData)
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
                    PasteType.TEXT_TYPE -> createTextMenuItems(pasteDataScope)
                    PasteType.COLOR_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.URL_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.HTML_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.RTF_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.IMAGE_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.FILE_TYPE -> createBaseMenuItems(pasteData)
                    else -> createLoadingMenuItems(pasteData)
                }
            }
        }
}
