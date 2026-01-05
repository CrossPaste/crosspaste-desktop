package com.crosspaste.listener

import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WindowTrigger
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.listener.DesktopShortcutKeys.Companion.HIDE_WINDOW
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_LOCAL_LAST
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_PLAIN_TEXT
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_PRIMARY_TYPE
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE_REMOTE_LAST
import com.crosspaste.listener.DesktopShortcutKeys.Companion.SHOW_MAIN
import com.crosspaste.listener.DesktopShortcutKeys.Companion.SHOW_SEARCH
import com.crosspaste.listener.DesktopShortcutKeys.Companion.TOGGLE_ENCRYPT
import com.crosspaste.listener.DesktopShortcutKeys.Companion.TOGGLE_PASTEBOARD_MONITORING
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteText
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.ioDispatcher
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopShortKeysAction(
    private val appFileChooser: AppFileChooser,
    private val appWindowManager: DesktopAppWindowManager,
    private val configManager: CommonConfigManager,
    private val currentPaste: CurrentPaste,
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
) : ShortcutKeysAction {

    private val logger = KotlinLogging.logger {}

    @Volatile
    override var actioning: Boolean = false

    @Volatile
    override var event: NativeKeyEvent? = null

    override val action: (String, NativeKeyEvent) -> Unit = { actionName, event ->
        this.event = event
        when (actionName) {
            PASTE_PLAIN_TEXT -> pastePlainText()
            PASTE_PRIMARY_TYPE -> pastePrimaryType()
            PASTE_LOCAL_LAST -> pasteLast(true)
            PASTE_REMOTE_LAST -> pasteLast(false)
            SHOW_MAIN -> showMainWindow()
            SHOW_SEARCH -> switchSearchWindow()
            HIDE_WINDOW -> hideWindow()
            TOGGLE_PASTEBOARD_MONITORING -> togglePasteboardMonitoring()
            TOGGLE_ENCRYPT -> toggleEncrypt()
        }
    }

    private fun mainRunAction(
        actionName: String,
        actionLogMessage: String,
        action: suspend () -> Unit,
    ) {
        mainCoroutineDispatcher.launch {
            runCatching {
                actioning = true
                logger.info { actionLogMessage }
                action()
            }.onFailure { e ->
                logger.error(e) { "Failed to run shortcut key action $actionName" }
            }.also {
                actioning = false
            }
        }
    }

    private fun showMainWindow() {
        mainRunAction(
            actionName = "OpenMainWindow",
            actionLogMessage = "Open main window",
        ) {
            appWindowManager.saveCurrentActiveAppInfo()
            appWindowManager.showMainWindow(WindowTrigger.SHORTCUT)
        }
    }

    private fun switchSearchWindow() {
        mainRunAction(
            actionName = "OpenSearchWindow",
            actionLogMessage = "Open search window",
        ) {
            appWindowManager.switchSearchWindow(WindowTrigger.SHORTCUT) {
                appWindowManager.saveCurrentActiveAppInfo()
            }
        }
    }

    private fun hideWindow() {
        mainRunAction(
            actionName = "HideWindow",
            actionLogMessage = "Hide window",
        ) {
            if (appWindowManager.getCurrentMainWindowInfo().show &&
                !appWindowManager.showMainDialog.value &&
                !appFileChooser.showFileDialog.value
            ) {
                appWindowManager.hideMainWindow()
            }

            if (appWindowManager.getCurrentSearchWindowInfo().show) {
                appWindowManager.hideSearchWindow()
            }
        }
    }

    private fun pastePlainText() {
        mainRunAction(
            actionName = "PastePlainText",
            actionLogMessage = "Paste plain text",
        ) {
            currentPaste.getCurrentPaste()?.let { pasteData ->
                withContext(ioDispatcher) {
                    pasteData.getPasteAppearItems().firstOrNull { it is PasteText }?.let {
                        handleCopyResult(
                            result =
                                pasteboardService.tryWritePasteboard(
                                    id = pasteData.id,
                                    pasteItem = it,
                                    localOnly = true,
                                    updateCreateTime = true,
                                ),
                        )
                    }
                }
            }
        }
    }

    private fun pastePrimaryType() {
        mainRunAction(
            actionName = "PastePrimaryType",
            actionLogMessage = "Paste primary type",
        ) {
            currentPaste.getCurrentPaste()?.let {
                withContext(ioDispatcher) {
                    handleCopyResult(
                        result =
                            pasteboardService.tryWritePasteboard(
                                pasteData = it,
                                localOnly = true,
                                updateCreateTime = true,
                            ),
                    )
                }
            }
        }
    }

    private fun pasteLast(local: Boolean) {
        mainRunAction(
            actionName = "PasteLast",
            actionLogMessage = "Paste ${if (local) "Local" else "Remote"} Last",
        ) {
            val result =
                pasteDao.searchPasteData(
                    searchTerms = listOf(),
                    local = local,
                    favorite = null,
                    limit = 1,
                )

            if (result.isNotEmpty()) {
                withContext(ioDispatcher) {
                    handleCopyResult(
                        result =
                            pasteboardService.tryWritePasteboard(
                                pasteData = result[0],
                                localOnly = true,
                                updateCreateTime = true,
                            ),
                    )
                }
            }
        }
    }

    private suspend fun handleCopyResult(result: Result<Unit?>) {
        result
            .onSuccess {
                appWindowManager.toPaste()
            }.onFailure {
                notificationManager.sendNotification(
                    title = { copyWriter -> copyWriter.getText("copy_failed") },
                    message = it.message?.let { message -> { message } },
                    messageType = MessageType.Error,
                )
            }
    }

    private fun togglePasteboardMonitoring() {
        mainRunAction(
            actionName = "TogglePasteboardMonitoring",
            actionLogMessage = "Toggle Pasteboard Monitoring",
        ) {
            pasteboardService.toggle()
        }
    }

    private fun toggleEncrypt() {
        mainRunAction(
            actionName = "ToggleEncrypt",
            actionLogMessage = "Toggle Encrypt",
        ) {
            configManager.updateConfig(
                "isEncryptSync",
                !configManager.getCurrentConfig().enableEncryptSync,
            )
        }
    }
}
