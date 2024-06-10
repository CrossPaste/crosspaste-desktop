package com.clipevery.listen

import com.clipevery.app.AppInfo
import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.ClipboardService
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.listener.ShortcutKeysAction
import com.clipevery.ui.base.DialogService
import com.clipevery.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.query.RealmQuery
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DesktopShortKeysAction(
    private val appInfo: AppInfo,
    private val clipDao: ClipDao,
    private val configManager: ConfigManager,
    private val appWindowManager: AppWindowManager,
    private val dialogService: DialogService,
    private val clipSearchService: ClipSearchService,
    private val clipboardService: ClipboardService,
) : ShortcutKeysAction {

    private val logger = KotlinLogging.logger {}

    private val mainDispatcherScope = CoroutineScope(mainDispatcher)

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
        mainDispatcherScope.launch(CoroutineName("OpenMainWindow")) {
            appWindowManager.activeMainWindow()
        }
    }

    private fun showSearchWindow() {
        logger.info { "Open search window" }
        mainDispatcherScope.launch(CoroutineName("OpenSearchWindow")) {
            clipSearchService.activeWindow()
        }
    }

    private fun hideWindow() {
        logger.info { "Hide window" }
        mainDispatcherScope.launch(CoroutineName("HideWindow")) {
            if (appWindowManager.showMainWindow && dialogService.dialogs.isEmpty()) {
                appWindowManager.unActiveMainWindow()
            }

            if (appWindowManager.showSearchWindow) {
                clipSearchService.unActiveWindow()
            }
        }
    }

    private fun pasteLast(local: Boolean) {
        logger.info { "paste ${if (local) "Local" else "Remote"} Last" }

        val appInstanceIdQuery: (RealmQuery<ClipData>) -> RealmQuery<ClipData> =
            if (local) {
                { it.query("appInstanceId == $0", appInfo.appInstanceId) }
            } else {
                { it.query("appInstanceId != $0", appInfo.appInstanceId) }
            }
        mainDispatcherScope.launch(CoroutineName("Paste")) {
            val result =
                clipDao.searchClipData(
                    searchTerms = listOf(),
                    favorite = null,
                    appInstanceIdQuery = appInstanceIdQuery,
                    limit = 1,
                )

            if (result.size > 0) {
                clipboardService.tryWriteClipboard(result[0], localOnly = true)
                appWindowManager.toPaste()
            }
        }
    }

    private fun switchMonitorPasteboard() {
        logger.info { "Switch Monitor Pasteboard" }
        mainDispatcherScope.launch(CoroutineName("SwitchMonitorPasteboard")) {
            clipboardService.toggle()
        }
    }

    private fun switchEncrypt() {
        logger.info { "Switch Encrypt" }
        mainDispatcherScope.launch(CoroutineName("SwitchEncrypt")) {
            configManager.updateConfig { config -> config.copy(isEncryptSync = !configManager.config.isEncryptSync) }
        }
    }
}
