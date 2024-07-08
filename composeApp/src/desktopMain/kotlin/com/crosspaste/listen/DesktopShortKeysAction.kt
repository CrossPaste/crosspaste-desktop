package com.crosspaste.listen

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.DialogService
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.query.RealmQuery
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

class DesktopShortKeysAction(
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
    private val configManager: ConfigManager,
    private val appWindowManager: AppWindowManager,
    private val dialogService: DialogService,
    private val pasteSearchService: PasteSearchService,
    private val pasteboardService: PasteboardService,
) : ShortcutKeysAction {

    private val logger = KotlinLogging.logger {}

    override val action: (String) -> Unit = { actionName ->
        when (actionName) {
            "Paste_Local_Last" -> pasteLast(true)
            "Paste_Remote_Last" -> pasteLast(false)
            "ShowMain" -> showMainWindow()
            "ShowSearch" -> showSearchWindow()
            "HideWindow" -> hideWindow()
            "SwitchMonitorPasteboard" -> switchMonitorPasteboard()
            "SwitchEncrypt" -> switchEncrypt()
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
            if (appWindowManager.showMainWindow && dialogService.dialogs.isEmpty()) {
                appWindowManager.unActiveMainWindow()
            }

            if (appWindowManager.showSearchWindow) {
                pasteSearchService.unActiveWindow()
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
                pasteboardService.tryWritePasteboard(result[0], localOnly = true)
                appWindowManager.toPaste()
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
