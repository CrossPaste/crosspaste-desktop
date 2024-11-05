package com.crosspaste.paste

import androidx.compose.foundation.ContextMenuItem
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.mongodb.kbson.ObjectId

class DesktopPasteMenuService(
    private val appWindowManager: AppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteRealm: PasteRealm,
    private val uiSupport: UISupport,
) : PasteMenuService {
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
                notificationManager.sendNotification(
                    message = copywriter.getText("copy_successful"),
                    messageType = MessageType.Success,
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
                    message = copywriter.getText("copy_successful"),
                    messageType = MessageType.Success,
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
            desktopAppWindowManager.setShowMainWindow(false)
        }
    }

    override fun deletePasteData(pasteData: PasteData) {
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
