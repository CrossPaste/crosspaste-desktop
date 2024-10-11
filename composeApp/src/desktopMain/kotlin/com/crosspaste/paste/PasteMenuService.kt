package com.crosspaste.paste

import androidx.compose.foundation.ContextMenuItem
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.mongodb.kbson.ObjectId

class PasteMenuService(
    private val appWindowManager: AppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteRealm: PasteRealm,
    private val uiSupport: UISupport,
) {
    private val desktopAppWindowManager = appWindowManager as DesktopAppWindowManager

    private val menuScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private fun copySingleFile(
        id: ObjectId,
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
                notificationManager.addNotification(
                    message = copywriter.getText("copy_successful"),
                    messageType = MessageType.Success,
                )
            },
        )
    }

    fun copyPasteData(pasteData: PasteData) {
        appWindowManager.doLongTaskInMain(
            scope = menuScope,
            task = {
                pasteboardService.tryWritePasteboard(
                    pasteData = pasteData,
                    localOnly = true,
                )
            },
            success = {
                notificationManager.addNotification(
                    message = copywriter.getText("copy_successful"),
                    messageType = MessageType.Success,
                )
            },
        )
    }

    fun openPasteData(
        pasteData: PasteData,
        index: Int = 0,
    ) {
        uiSupport.openPasteData(pasteData, index)
        desktopAppWindowManager.setShowMainWindow(false)
    }

    fun deletePasteData(pasteData: PasteData) {
        appWindowManager.doLongTaskInMain(
            scope = menuScope,
            task = {
                pasteRealm.markDeletePasteData(pasteData.id)
            },
        )
    }

    fun fileMenuItemsProvider(
        pasteData: PasteData,
        pasteItem: PasteItem,
        index: Int = 0,
    ): () -> List<ContextMenuItem> {
        return {
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
    }

    fun pasteMenuItemsProvider(pasteData: PasteData): () -> List<ContextMenuItem> {
        return {
            listOf(
                ContextMenuItem(copywriter.getText("copy")) {
                    copyPasteData(pasteData)
                },
                ContextMenuItem(copywriter.getText("open")) {
                    openPasteData(pasteData)
                },
                ContextMenuItem(copywriter.getText("delete")) {
                    deletePasteData(pasteData)
                },
            )
        }
    }
}
