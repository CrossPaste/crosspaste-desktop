package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

fun getDesktopAppWindowManager(
    appSize: DesktopAppSize,
    configManager: DesktopConfigManager,
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    lazyShortcutKeysAction: Lazy<ShortcutKeysAction>,
    platform: Platform,
    userDataPathProvider: UserDataPathProvider,
): DesktopAppWindowManager =
    if (platform.isMacos()) {
        MacAppWindowManager(
            appSize,
            configManager,
            lazyShortcutKeys,
            userDataPathProvider,
        )
    } else if (platform.isWindows()) {
        WinAppWindowManager(
            appSize,
            configManager,
            lazyShortcutKeys,
            lazyShortcutKeysAction,
            userDataPathProvider,
        )
    } else if (platform.isLinux()) {
        LinuxAppWindowManager(
            appSize,
            configManager,
            lazyShortcutKeys,
            lazyShortcutKeysAction,
            userDataPathProvider,
        )
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }

private data class WindowScheduledTask(
    val delayMillis: Long,
    val action: suspend () -> Unit,
)

enum class WindowTrigger {
    MENU,
    SHORTCUT,
    TRAY_ICON,
    INIT,
    SYSTEM,
}

data class WindowInfo(
    val show: Boolean,
    val state: WindowState,
    val trigger: WindowTrigger,
)

abstract class DesktopAppWindowManager(
    val appSize: DesktopAppSize,
    val configManager: DesktopConfigManager,
) : AppWindowManager() {

    companion object {
        private const val MAIN_WINDOW_TITLE: String = "CrossPaste"

        private const val SEARCH_WINDOW_TITLE = "CrossPaste Search"

        // only use in Windows
        const val MENU_WINDOW_TITLE: String = "CrossPaste Menu"
    }

    protected val logger: KLogger = KotlinLogging.logger {}

    val mainWindowTitle: String = MAIN_WINDOW_TITLE

    val searchWindowTitle: String = SEARCH_WINDOW_TITLE

    protected val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _mainWindowInfo =
        MutableStateFlow(
            WindowInfo(
                show = false,
                state =
                    WindowState(
                        isMinimized = false,
                        size = appSize.mainWindowSize,
                        position = WindowPosition(Alignment.Center),
                    ),
                trigger = WindowTrigger.INIT,
            ),
        )
    val mainWindowInfo: StateFlow<WindowInfo> = _mainWindowInfo

    private val _alwaysOnTopMainWindow = MutableStateFlow(false)
    val alwaysOnTopMainWindow: StateFlow<Boolean> = _alwaysOnTopMainWindow

    var mainComposeWindow: ComposeWindow? = null

    private val _searchWindowInfo =
        MutableStateFlow(
            WindowInfo(
                show = false,
                state = appSize.getSearchWindowState(),
                trigger = WindowTrigger.INIT,
            ),
        )
    var searchWindowInfo: StateFlow<WindowInfo> = _searchWindowInfo

    private val hideSearchWindowCallbacks = mutableListOf<WindowScheduledTask>()

    var searchComposeWindow: ComposeWindow? = null

    init {
        ioScope.launch {
            searchWindowInfo.collectLatest { info ->
                if (!info.show) {
                    hideSearchCallback()
                }
            }
        }
    }

    abstract fun startWindowService()

    abstract fun stopWindowService()

    private suspend fun hideSearchCallback() {
        runCatching {
            val tasksSnapshot =
                synchronized(hideSearchWindowCallbacks) {
                    hideSearchWindowCallbacks.toList()
                }

            var accumulatedTime = 0L

            tasksSnapshot.forEach { task ->
                val waitTime = task.delayMillis - accumulatedTime
                if (waitTime > 0) {
                    delay(waitTime)
                }

                logger.debug { "Executing callback scheduled for ${task.delayMillis}ms" }
                task.action()

                accumulatedTime = task.delayMillis
            }
        }.onFailure { e ->
            if (e !is CancellationException) {
                logger.error(e) { "Error in hide search window callback" }
            }
        }
    }

    fun hideMainWindow() {
        _mainWindowInfo.value =
            _mainWindowInfo.value.copy(
                show = false,
            )
    }

    fun showMainWindow(windowTrigger: WindowTrigger) {
        _mainWindowInfo.value =
            _mainWindowInfo.value.copy(
                show = true,
                trigger = windowTrigger,
            )
    }

    abstract fun saveCurrentActiveAppInfo()

    abstract suspend fun focusMainWindow(windowTrigger: WindowTrigger)

    fun hideSearchWindow() {
        _searchWindowInfo.value = _searchWindowInfo.value.copy(show = false)
    }

    fun showSearchWindow(windowTrigger: WindowTrigger) {
        _searchWindowInfo.value =
            _searchWindowInfo.value.copy(
                show = true,
                state = appSize.getSearchWindowState(),
                trigger = windowTrigger,
            )
    }

    abstract suspend fun focusSearchWindow(windowTrigger: WindowTrigger)

    fun switchAlwaysOnTopMainWindow() {
        _alwaysOnTopMainWindow.value = !_alwaysOnTopMainWindow.value
    }

    abstract fun getCurrentActiveAppName(): String?

    abstract suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean = { false })

    abstract suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean = { false },
    )

    abstract fun getPrevAppName(): Flow<String?>

    fun registerHideSearchWindowCallback(
        delayMillis: Long,
        action: suspend () -> Unit,
    ) {
        synchronized(hideSearchWindowCallbacks) {
            hideSearchWindowCallbacks.add(WindowScheduledTask(delayMillis, action))
            hideSearchWindowCallbacks.sortBy { it.delayMillis }
        }
    }
}
