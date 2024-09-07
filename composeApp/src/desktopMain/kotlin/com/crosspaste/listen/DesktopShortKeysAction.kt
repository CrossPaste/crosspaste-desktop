package com.crosspaste.listen

import com.crosspaste.app.AppInfo
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.listen.DesktopShortcutKeys.Companion.HIDE_WINDOW
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_LOCAL_LAST
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_PLAIN_TEXT
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_PRIMARY_TYPE
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE_REMOTE_LAST
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SHOW_MAIN
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SHOW_SEARCH
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SWITCH_ENCRYPT
import com.crosspaste.listen.DesktopShortcutKeys.Companion.SWITCH_MONITOR_PASTEBOARD
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.DesktopPasteSearchService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.PasteText
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.query.RealmQuery
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

class DesktopShortKeysAction(
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
    private val configManager: ConfigManager,
    private val currentPaste: CurrentPaste,
    private val appWindowManager: DesktopAppWindowManager,
    private val pasteSearchService: DesktopPasteSearchService,
    private val pasteboardService: PasteboardService,
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
            SWITCH_MONITOR_PASTEBOARD -> switchMonitorPasteboard()
            SWITCH_ENCRYPT -> switchEncrypt()
        }
    }

    private fun showMainWindow() {
        logger.info { "Open main window" }
        mainCoroutineDispatcher.launch(CoroutineName("OpenMainWindow")) {
            appWindowManager.activeMainWindow()
        }
    }

    private fun showSearchWindow() {
        logger.info { "Open search window" }
        mainCoroutineDispatcher.launch(CoroutineName("OpenSearchWindow")) {
            pasteSearchService.activeWindow()
        }
    }

    private fun hideWindow() {
        logger.info { "Hide window" }
        mainCoroutineDispatcher.launch(CoroutineName("HideWindow")) {
            if (appWindowManager.showMainWindow &&
                !appWindowManager.showMainDialog &&
                !appWindowManager.showFileDialog
            ) {
                appWindowManager.unActiveMainWindow()
            }

            if (appWindowManager.showSearchWindow) {
                pasteSearchService.unActiveWindow()
            }
        }
    }

    private fun pastePlainText() {
        logger.info { "Paste Plain Text" }
        mainCoroutineDispatcher.launch(CoroutineName("PastePlainText")) {
            currentPaste.getCurrentPaste()?.let { pasteData ->
                mainCoroutineDispatcher.launch(ioDispatcher) {
                    pasteData.getPasteAppearItems().firstOrNull { it is PasteText }?.let {
                        pasteboardService.tryWritePasteboard(
                            id = pasteData.id,
                            pasteItem = it,
                            localOnly = true,
                            updateCreateTime = true,
                        )
                        appWindowManager.toPaste()
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
                    pasteboardService.tryWritePasteboard(
                        pasteData = it,
                        localOnly = true,
                        primary = true,
                        updateCreateTime = true,
                    )
                    appWindowManager.toPaste()
                }
            }
        }
    }

    private fun pasteLast(local: Boolean) {
        logger.info { "paste ${if (local) "Local" else "Remote"} Last" }

        val appInstanceIdQuery: (RealmQuery<PasteData>) -> RealmQuery<PasteData> =
            if (local) {
                { it.query("appInstanceId == $0", appInfo.appInstanceId) }
            } else {
                { it.query("appInstanceId != $0", appInfo.appInstanceId) }
            }
        mainCoroutineDispatcher.launch(CoroutineName("Paste")) {
            val result =
                pasteDao.searchPasteData(
                    searchTerms = listOf(),
                    favorite = null,
                    appInstanceIdQuery = appInstanceIdQuery,
                    limit = 1,
                )

            if (result.size > 0) {
                mainCoroutineDispatcher.launch(ioDispatcher) {
                    pasteboardService.tryWritePasteboard(
                        pasteData = result[0],
                        localOnly = true,
                        updateCreateTime = true,
                    )
                    appWindowManager.toPaste()
                }
            }
        }
    }

    private fun switchMonitorPasteboard() {
        logger.info { "Switch Monitor Pasteboard" }
        mainCoroutineDispatcher.launch(CoroutineName("SwitchMonitorPasteboard")) {
            pasteboardService.toggle()
        }
    }

    private fun switchEncrypt() {
        logger.info { "Switch Encrypt" }
        mainCoroutineDispatcher.launch(CoroutineName("SwitchEncrypt")) {
            configManager.updateConfig("isEncryptSync", !configManager.config.isEncryptSync)
        }
    }
}
