package com.crosspaste.paste

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Check
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WindowTrigger
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.OCRModule
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.utils.ioDispatcher
import com.dzirbel.contextmenu.ContextMenuDivider
import com.dzirbel.contextmenu.ContextMenuGroup
import com.dzirbel.contextmenu.MaterialContextMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopPasteMenuService(
    appWindowManager: AppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
    private val pasteSearchViewModel: PasteSearchViewModel,
    private val ocrModule: OCRModule,
    private val uiSupport: UISupport,
    private val userDataPathProvider: UserDataPathProvider,
) : PasteMenuService {

    companion object {
        private const val CUT_UNDO_DELAY_MS = 60_000L
    }

    private val desktopAppWindowManager = appWindowManager as DesktopAppWindowManager

    private val menuScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _pendingCutPasteId = MutableStateFlow<Long?>(null)
    val pendingCutPasteId: StateFlow<Long?> = _pendingCutPasteId.asStateFlow()
    private var pendingCutJob: Job? = null

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

    private fun cutPasteData(pasteData: PasteData) {
        menuScope.launch {
            pasteboardService
                .tryWritePasteboard(
                    pasteData = pasteData,
                    localOnly = true,
                ).onSuccess {
                    pendingCutJob?.cancel()
                    _pendingCutPasteId.value = null

                    pendingCutJob =
                        menuScope.launch {
                            pasteDao.cutPasteData(pasteData.id, CUT_UNDO_DELAY_MS)
                            _pendingCutPasteId.value = pasteData.id
                            delay(CUT_UNDO_DELAY_MS)
                            _pendingCutPasteId.value = null
                            pendingCutJob = null
                        }
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

    fun cancelCut() {
        menuScope.launch {
            pendingCutJob?.cancel()
            pendingCutJob = null
            _pendingCutPasteId.value?.let { id ->
                pasteDao.updatePasteState(id, PasteState.LOADED)
            }
            _pendingCutPasteId.value = null
        }
    }

    override fun openPasteData(
        pasteData: PasteData,
        index: Int,
    ) {
        menuScope.launch {
            uiSupport.openPasteData(pasteData, index)
            val pasteType = pasteData.getType()
            if (pasteType.isText() || pasteType.isHtml()) {
                if (!desktopAppWindowManager.getCurrentSearchWindowInfo().show) {
                    desktopAppWindowManager.showMainWindow(WindowTrigger.SYSTEM)
                }
            } else if (pasteType.isColor()) {
                desktopAppWindowManager.showMainWindow(WindowTrigger.SYSTEM)
            } else {
                desktopAppWindowManager.hideMainWindow()
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
                pasteboardService
                    .tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        updateCreateTime = true,
                    ).isSuccess
            }
        }
    }

    override fun quickPasteFromSearchWindow(pasteData: PasteData) {
        menuScope.launch {
            desktopAppWindowManager.hideSearchWindowAndPaste(0) {
                pasteboardService
                    .tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        updateCreateTime = true,
                    ).isSuccess
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

    private fun createCutContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("cut")) {
            cutPasteData(pasteData)
        }

    private fun createDeleteContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("delete")) {
            deletePasteData(pasteData)
        }

    private fun createEditContextMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuItem(copywriter.getText("edit")) {
            openPasteData(pasteData)
        }

    private fun createPinTagMenuItem(pasteData: PasteData): ContextMenuItem =
        ContextMenuGroup(copywriter.getText("pin")) {
            val tagList = pasteSearchViewModel.tagList.value
            if (tagList.isEmpty()) {
                listOf(
                    ContextMenuItem(copywriter.getText("empty")) {
                    },
                )
            } else {
                val tagIdList = pasteDao.getPasteTagsBlock(pasteData.id)

                tagList.map { tag ->
                    MaterialContextMenuItem(
                        label = tag.name,
                        onClick = {
                            pasteDao.switchPinPasteTagBlock(pasteData.id, tag.id)
                        },
                        leadingIcon = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(small)
                                        .background(color = Color(tag.color.toInt()), shape = CircleShape),
                            )
                        },
                        trailingIcon = {
                            if (tagIdList.contains(tag.id)) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Check,
                                    contentDescription = "Checked",
                                    tint = AppUIColors.importantColor,
                                )
                            }
                        },
                    )
                }
            }
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
            ContextMenuDivider,
            createDeleteContextMenuItem(pasteData),
        )

    private fun createBaseMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createOpenContextMenuItem(pasteData),
            createPinTagMenuItem(pasteData),
            ContextMenuDivider,
            createDeleteContextMenuItem(pasteData),
        )

    private fun createFileTypeMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        buildList {
            add(createCopyContextMenuItem(pasteData))
            val pasteFiles = pasteData.getPasteItem(PasteFiles::class)
            if (pasteFiles != null && !pasteFiles.isRefFiles()) {
                add(createCutContextMenuItem(pasteData))
            }
            add(createOpenContextMenuItem(pasteData))
            add(createPinTagMenuItem(pasteData))
            add(ContextMenuDivider)
            add(createDeleteContextMenuItem(pasteData))
        }

    private fun createTextMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        listOf(
            createCopyContextMenuItem(pasteData),
            createEditContextMenuItem(pasteData),
            createPinTagMenuItem(pasteData),
            ContextMenuDivider,
            createDeleteContextMenuItem(pasteData),
        )

    private fun createImageMenuItems(pasteData: PasteData): List<ContextMenuItem> =
        buildList {
            add(createCopyContextMenuItem(pasteData))
            val pasteFiles = pasteData.getPasteItem(PasteFiles::class)
            if (pasteFiles != null && !pasteFiles.isRefFiles()) {
                add(createCutContextMenuItem(pasteData))
            }
            add(createEditContextMenuItem(pasteData))
            add(createExtractTextContextMenuItem(pasteData))
            add(createPinTagMenuItem(pasteData))
            add(ContextMenuDivider)
            add(createDeleteContextMenuItem(pasteData))
        }

    fun mainPasteMenuItemsProvider(pasteData: PasteData): () -> List<ContextMenuItem> =
        {
            if (pasteData.pasteState == PasteState.LOADING) {
                createLoadingMenuItems(pasteData)
            } else {
                when (pasteData.getType()) {
                    PasteType.TEXT_TYPE -> createTextMenuItems(pasteData)
                    PasteType.COLOR_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.URL_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.HTML_TYPE -> createTextMenuItems(pasteData)
                    PasteType.RTF_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.IMAGE_TYPE -> createImageMenuItems(pasteData)
                    PasteType.FILE_TYPE -> createFileTypeMenuItems(pasteData)
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
                    PasteType.HTML_TYPE -> createTextMenuItems(pasteData)
                    PasteType.RTF_TYPE -> createBaseMenuItems(pasteData)
                    PasteType.IMAGE_TYPE -> createImageMenuItems(pasteData)
                    PasteType.FILE_TYPE -> createFileTypeMenuItems(pasteData)
                    else -> createLoadingMenuItems(pasteData)
                }
            }
        }
}
