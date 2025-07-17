package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.ui.Pasteboard
import com.crosspaste.ui.ScreenContext
import com.crosspaste.ui.ScreenType
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

abstract class DesktopAppWindowManager(
    val appSize: DesktopAppSize,
    val configManager: DesktopConfigManager,
) : AppWindowManager {

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

    private val _screenContext = MutableStateFlow(ScreenContext(Pasteboard))
    override val screenContext: StateFlow<ScreenContext> = _screenContext.asStateFlow()

    private val _showMainWindow = MutableStateFlow(false)
    val showMainWindow: StateFlow<Boolean> = _showMainWindow

    private val _isMinimizedMainWindow = MutableStateFlow(false)
    val isMinimizedMainWindow: StateFlow<Boolean> = _isMinimizedMainWindow

    private val _alwaysOnTopMainWindow = MutableStateFlow(false)
    val alwaysOnTopMainWindow: StateFlow<Boolean> = _alwaysOnTopMainWindow

    var mainComposeWindow: ComposeWindow? = null

    private val _showMainDialog = MutableStateFlow(false)
    override val showMainDialog: StateFlow<Boolean> = _showMainDialog

    private val _showSearchWindow = MutableStateFlow(false)
    var showSearchWindow: StateFlow<Boolean> = _showSearchWindow

    private val _searchWindowState =
        MutableStateFlow(
            appSize.getSearchWindowState(),
        )
    val searchWindowState: StateFlow<WindowState> = _searchWindowState

    var searchComposeWindow: ComposeWindow? = null

    fun hideMainWindow() {
        _showMainWindow.value = false
    }

    fun showMainWindow() {
        _isMinimizedMainWindow.value = false
        _showMainWindow.value = true
    }

    fun hideSearchWindow() {
        _showSearchWindow.value = false
    }

    fun showSearchWindow() {
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

    abstract suspend fun recordActiveInfoAndShowMainWindow(useShortcutKeys: Boolean)

    abstract suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean = { false })

    abstract suspend fun recordActiveInfoAndShowSearchWindow(useShortcutKeys: Boolean)

    abstract suspend fun hideSearchWindowAndPaste(preparePaste: suspend () -> Boolean = { false })

    abstract fun getPrevAppName(): Flow<String?>

    override fun returnScreen() {
        _screenContext.value = screenContext.value.returnNext()
    }

    override fun setScreen(screenContext: ScreenContext) {
        _screenContext.value = screenContext
    }

    override fun toScreen(
        screenType: ScreenType,
        context: Any,
    ) {
        _screenContext.value =
            if (context == Unit) {
                ScreenContext(screenType, _screenContext.value)
            } else {
                ScreenContext(screenType, _screenContext.value, context)
            }
    }
}
