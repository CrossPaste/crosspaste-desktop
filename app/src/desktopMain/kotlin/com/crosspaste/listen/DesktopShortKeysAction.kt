package com.crosspaste.listen

import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.listen.DesktopShortcutKeys.Companion.HIDE_WINDOW
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_LOCAL_LAST
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_PLAIN_TEXT
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_PRIMARY_TYPE
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_REMOTE_LAST
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SHOW_MAIN
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SHOW_SEARCH
import com.crosspaste.listen.DesktopShortcutKeys.Companion.TOGGLE_ENCRYPT
import com.crosspaste.listen.DesktopShortcutKeys.Companion.TOGGLE_PASTEBOARD_MONITORING
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteText
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

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

    override val action: (String) -> Unit = { actionName ->
        when (actionName) {
            PASTE_PLAIN_TEXT -> pastePlainText()
            PASTE_PRIMARY_TYPE -> pastePrimaryType()
            PASTE_LOCAL_LAST -> pasteLast(true)
            PASTE_REMOTE_LAST -> pasteLast(false)
            SHOW_MAIN -> showMainWindow()
            SHOW_SEARCH -> showSearchWindow()
            HIDE_WINDOW -> hideWindow()
            TOGGLE_PASTEBOARD_MONITORING -> togglePasteboardMonitoring()
            TOGGLE_ENCRYPT -> toggleEncrypt()
        }
    }

    private fun showMainWindow() {
        logger.info { "Open main window" }
        mainCoroutineDispatcher.launch(CoroutineName("OpenMainWindow")) {
            appWindowManager.recordActiveInfoAndShowMainWindow(true)
        }
    }

    private fun showSearchWindow() {
        logger.info { "Open search window" }
        mainCoroutineDispatcher.launch(CoroutineName("OpenSearchWindow")) {
            appWindowManager.recordActiveInfoAndShowSearchWindow(true)
        }
    }

    private fun hideWindow() {
        logger.info { "Hide window" }
        mainCoroutineDispatcher.launch(CoroutineName("HideWindow")) {
            if (appWindowManager.showMainWindow.value &&
                !appWindowManager.showMainDialog.value &&
                !appFileChooser.showFileDialog.value
            ) {
                appWindowManager.hideMainWindow()
            }

            if (appWindowManager.showSearchWindow.value) {
                appWindowManager.hideSearchWindow()
            }
        }
    }

    private fun pastePlainText() {
        logger.info { "Paste Plain Text" }
        mainCoroutineDispatcher.launch(CoroutineName("PastePlainText")) {
            currentPaste.getCurrentPaste()?.let { pasteData ->
                mainCoroutineDispatcher.launch(ioDispatcher) {
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
        logger.info { "Paste Primary Type" }
        mainCoroutineDispatcher.launch(CoroutineName("PastePrimaryType")) {
            currentPaste.getCurrentPaste()?.let {
                mainCoroutineDispatcher.launch(ioDispatcher) {
                    handleCopyResult(
                        result =
                            pasteboardService.tryWritePasteboard(
                                pasteData = it,
                                localOnly = true,
                                primary = true,
                                updateCreateTime = true,
                            ),
                    )
                }
            }
        }
    }

    private fun pasteLast(local: Boolean) {
        logger.info { "paste ${if (local) "Local" else "Remote"} Last" }
        mainCoroutineDispatcher.launch(CoroutineName("Paste")) {
            val result =
                pasteDao.searchPasteData(
                    searchTerms = listOf(),
                    local = local,
                    favorite = null,
                    limit = 1,
                )

            if (result.isNotEmpty()) {
                mainCoroutineDispatcher.launch(ioDispatcher) {
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
                    title = { it.getText("copy_failed") },
                    message = it.message?.let { message -> { it -> message } },
                    messageType = MessageType.Error,
                )
            }
    }

    private fun togglePasteboardMonitoring() {
        logger.info { "Toggle Pasteboard Monitoring" }
        mainCoroutineDispatcher.launch(CoroutineName("ToggleMonitorPasteboard")) {
            pasteboardService.toggle()
        }
    }

    private fun toggleEncrypt() {
        logger.info { "Toggle Encrypt ${!configManager.getCurrentConfig().enableEncryptSync}" }
        mainCoroutineDispatcher.launch(CoroutineName("ToggleEncrypt")) {
            configManager.updateConfig(
                "isEncryptSync",
                !configManager.getCurrentConfig().enableEncryptSync,
            )
        }
    }
}
