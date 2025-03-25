package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.ui.PastePreview
import com.crosspaste.ui.ScreenContext
import com.crosspaste.ui.ScreenType
import com.crosspaste.utils.Memoize
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Cursor
import java.awt.Rectangle

fun getDesktopAppWindowManager(
    appSize: AppSize,
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    activeGraphicsDevice: ActiveGraphicsDevice,
    userDataPathProvider: UserDataPathProvider,
): DesktopAppWindowManager {
    val platform = getPlatform()
    return if (platform.isMacos()) {
        MacAppWindowManager(
            appSize,
            lazyShortcutKeys,
            activeGraphicsDevice,
            userDataPathProvider,
        )
    } else if (platform.isWindows()) {
        WinAppWindowManager(
            appSize,
            lazyShortcutKeys,
            activeGraphicsDevice,
            userDataPathProvider,
        )
    } else if (platform.isLinux()) {
        LinuxAppWindowManager(
            appSize,
            lazyShortcutKeys,
            activeGraphicsDevice,
            userDataPathProvider,
        )
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }
}

abstract class DesktopAppWindowManager(
    val appSize: AppSize,
) : AppWindowManager {

    companion object {
        const val MAIN_WINDOW_TITLE: String = "CrossPaste"

        const val SEARCH_WINDOW_TITLE: String = "CrossPaste Search"

        // only use in Windows
        const val MENU_WINDOW_TITLE: String = "CrossPaste Menu"
    }

    protected val logger: KLogger = KotlinLogging.logger {}

    protected val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _screenContext = MutableStateFlow(ScreenContext(PastePreview))
    override val screenContext: StateFlow<ScreenContext> = _screenContext.asStateFlow()

    private val _firstLaunchCompleted = MutableStateFlow(false)
    override val firstLaunchCompleted: StateFlow<Boolean> = _firstLaunchCompleted

    private val _showMainWindow = MutableStateFlow(false)
    val showMainWindow: StateFlow<Boolean> = _showMainWindow

    private val _mainWindowState =
        MutableStateFlow(
            WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition.PlatformDefault,
                size = appSize.mainWindowSize,
            ),
        )
    val mainWindowState: StateFlow<WindowState> = _mainWindowState

    var mainComposeWindow: ComposeWindow? = null

    val mainFocusRequester = FocusRequester()

    private val _showMainDialog = MutableStateFlow(false)
    override val showMainDialog: StateFlow<Boolean> = _showMainDialog

    private val _showSearchWindow = MutableStateFlow(false)
    var showSearchWindow: StateFlow<Boolean> = _showSearchWindow

    private val _searchWindowState =
        MutableStateFlow(
            WindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition.Aligned(Alignment.Center),
                size = appSize.searchWindowSize,
            ),
        )
    val searchWindowState: StateFlow<WindowState> = _searchWindowState

    var searchComposeWindow: ComposeWindow? = null

    val searchFocusRequester = FocusRequester()

    protected val calPosition: (Rectangle) -> WindowPosition =
        Memoize.memoize { bounds ->
            val windowSize = appSize.searchWindowSize
            WindowPosition(
                x = (bounds.x.dp + ((bounds.width.dp - windowSize.width) / 2)),
                y = (bounds.y.dp + ((bounds.height.dp - windowSize.height) / 2)),
            )
        }

    fun setFirstLaunchCompleted(firstLaunchCompleted: Boolean) {
        _firstLaunchCompleted.value = firstLaunchCompleted
    }

    fun setShowMainWindow(showMainWindow: Boolean) {
        _showMainWindow.value = showMainWindow
    }

    fun setShowSearchWindow(showSearchWindow: Boolean) {
        _showSearchWindow.value = showSearchWindow
    }

    fun setMainWindowState(windowState: WindowState) {
        _mainWindowState.value = windowState
    }

    fun getMainWindowState(): WindowState {
        return mainWindowState.value
    }

    fun setSearchWindowState(windowState: WindowState) {
        _searchWindowState.value = windowState
    }

    fun getSearchWindowState(): WindowState {
        return searchWindowState.value
    }

    override fun resetMainCursor() {
        mainComposeWindow?.cursor = Cursor.getDefaultCursor()
    }

    override fun setMainCursorWait() {
        mainComposeWindow?.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
    }

    override fun resetSearchCursor() {
        searchComposeWindow?.cursor = Cursor.getDefaultCursor()
    }

    override fun setSearchCursorWait() {
        searchComposeWindow?.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
    }

    abstract fun getCurrentActiveAppName(): String?

    abstract suspend fun activeMainWindow()

    abstract suspend fun unActiveMainWindow(preparePaste: suspend () -> Boolean = { false })

    suspend fun switchMainWindow() {
        if (showMainWindow.value) {
            unActiveMainWindow()
        } else {
            activeMainWindow()
        }
    }

    abstract suspend fun activeSearchWindow()

    abstract suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean = { false })

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
