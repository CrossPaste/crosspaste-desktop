package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.ShortcutKeys
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
import java.awt.Desktop
import kotlin.coroutines.cancellation.CancellationException

fun getDesktopAppWindowManager(
    appSize: DesktopAppSize,
    configManager: DesktopConfigManager,
    lazyShortcutKeys: Lazy<ShortcutKeys>,
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
            userDataPathProvider,
        )
    } else if (platform.isLinux()) {
        LinuxAppWindowManager(
            appSize,
            configManager,
            lazyShortcutKeys,
            userDataPathProvider,
        )
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }

private data class WindowScheduledTask(
    val delayMillis: Long,
    val action: suspend () -> Unit,
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

    private val _showMainWindow = MutableStateFlow(false)
    val showMainWindow: StateFlow<Boolean> = _showMainWindow

    private val _alwaysOnTopMainWindow = MutableStateFlow(false)
    val alwaysOnTopMainWindow: StateFlow<Boolean> = _alwaysOnTopMainWindow

    var mainComposeWindow: ComposeWindow? = null

    private val _showSearchWindow = MutableStateFlow(false)
    var showSearchWindow: StateFlow<Boolean> = _showSearchWindow

    private val hideSearchWindowCallbacks = mutableListOf<WindowScheduledTask>()

    private val _mainWindowState =
        MutableStateFlow(
            WindowState(
                isMinimized = false,
                size = appSize.mainWindowSize,
                position = WindowPosition(Alignment.Center),
            ),
        )
    val mainWindowState: StateFlow<WindowState> = _mainWindowState

    private val _searchWindowState =
        MutableStateFlow(
            appSize.getSearchWindowState(),
        )
    val searchWindowState: StateFlow<WindowState> = _searchWindowState

    var searchComposeWindow: ComposeWindow? = null

    init {
        ioScope.launch {
            showSearchWindow.collectLatest { isShown ->
                if (!isShown) {
                    hideSearchCallback()
                }
            }
        }
    }

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
        _showMainWindow.value = false
    }

    protected fun showMainWindow() {
        _mainWindowState.value =
            WindowState(
                isMinimized = false,
                size = appSize.mainWindowSize,
                position = _mainWindowState.value.position,
            )
        _showMainWindow.value = true
    }

    fun hideSearchWindow() {
        _showSearchWindow.value = false
    }

    protected fun showSearchWindow() {
        _showSearchWindow.value = true
    }

    fun switchAlwaysOnTopMainWindow() {
        _alwaysOnTopMainWindow.value = !_alwaysOnTopMainWindow.value
    }

    fun setSearchWindowState(windowState: WindowState) {
        _searchWindowState.value = windowState
    }

    fun getSearchWindowState(): WindowState = searchWindowState.value

    abstract fun getCurrentActiveAppName(): String?

    abstract suspend fun showMainWindow(
        recordInfo: Boolean = true,
        useShortcutKeys: Boolean = false,
    )

    abstract suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean = { false })

    abstract suspend fun showSearchWindow(
        recordInfo: Boolean = true,
        useShortcutKeys: Boolean = false,
    )

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

    protected fun requestForeground() {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().requestForeground(true)
        }
    }
}
