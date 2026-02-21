package com.crosspaste.app

import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

fun getDesktopAppWindowManager(
    appSize: DesktopAppSize,
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    lazyShortcutKeysAction: Lazy<ShortcutKeysAction>,
    platform: Platform,
    userDataPathProvider: UserDataPathProvider,
): DesktopAppWindowManager =
    if (platform.isMacos()) {
        MacAppWindowManager(
            appSize,
            lazyShortcutKeys,
            userDataPathProvider,
        )
    } else if (platform.isWindows()) {
        WinAppWindowManager(
            appSize,
            lazyShortcutKeys,
            userDataPathProvider,
        )
    } else if (platform.isLinux()) {
        LinuxAppWindowManager(
            appSize,
            lazyShortcutKeys,
            lazyShortcutKeysAction,
            userDataPathProvider,
        )
    } else {
        throw IllegalStateException("Unsupported platform: $platform")
    }

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
) : AppWindowManager() {

    companion object {
        private const val MAIN_WINDOW_TITLE: String = "CrossPaste"

        private const val SEARCH_WINDOW_TITLE = "CrossPaste Search"

        private const val BUBBLE_WINDOW_TITLE = "CrossPaste Editor"
    }

    protected val logger: KLogger = KotlinLogging.logger {}

    val mainWindowTitle: String = MAIN_WINDOW_TITLE

    val searchWindowTitle: String = SEARCH_WINDOW_TITLE

    val bubbleWindowTitle: String = BUBBLE_WINDOW_TITLE

    protected val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _mainWindowInfo =
        MutableStateFlow(
            WindowInfo(
                show = false,
                state =
                    WindowState(
                        isMinimized = false,
                        size = appSize.appSizeValue.value.mainWindowSize,
                        position = WindowPosition(Alignment.Center),
                    ),
                trigger = WindowTrigger.INIT,
            ),
        )
    val mainWindowInfo: StateFlow<WindowInfo> = _mainWindowInfo

    private val _alwaysOnTopMainWindow = MutableStateFlow(false)
    val alwaysOnTopMainWindow: StateFlow<Boolean> = _alwaysOnTopMainWindow

    var mainComposeWindow: ComposeWindow? = null
        set(value) {
            if (field != value) {
                field = value
                onMainComposeWindowChanged(value)
            }
        }

    private val _searchWindowInfo =
        MutableStateFlow(
            WindowInfo(
                show = false,
                state = appSize.getSearchWindowState(true),
                trigger = WindowTrigger.INIT,
            ),
        )
    var searchWindowInfo: StateFlow<WindowInfo> = _searchWindowInfo

    var searchComposeWindow: ComposeWindow? = null
        set(value) {
            if (field != value) {
                field = value
                onSearchComposeWindowChanged(value)
            }
        }

    data class BubbleWindowInfo(
        val show: Boolean = false,
        val pasteId: Long = 0L,
    )

    private val _bubbleWindowInfo = MutableStateFlow(BubbleWindowInfo())
    val bubbleWindowInfo: StateFlow<BubbleWindowInfo> = _bubbleWindowInfo

    var bubbleComposeWindow: ComposeWindow? = null
        set(value) {
            if (field != value) {
                field = value
                onBubbleComposeWindowChanged(value)
            }
        }

    fun showBubbleWindow(pasteId: Long) {
        _bubbleWindowInfo.value = BubbleWindowInfo(show = true, pasteId = pasteId)
    }

    fun hideBubbleWindow() {
        _bubbleWindowInfo.value = BubbleWindowInfo(show = false)
    }

    fun isBubbleWindowVisible(): Boolean = _bubbleWindowInfo.value.show

    abstract suspend fun focusBubbleWindow()

    abstract fun startWindowService()

    abstract fun stopWindowService()

    fun getCurrentMainWindowInfo(): WindowInfo = _mainWindowInfo.value

    fun getCurrentSearchWindowInfo(): WindowInfo = _searchWindowInfo.value

    fun hideMainWindow() {
        _mainWindowInfo.value =
            _mainWindowInfo.value.copy(
                show = false,
            )
    }

    fun showMainWindow(windowTrigger: WindowTrigger) {
        if (!_mainWindowInfo.value.show) {
            _mainWindowInfo.value =
                _mainWindowInfo.value.copy(
                    show = true,
                    trigger = windowTrigger,
                )
        } else {
            mainCoroutineDispatcher.launch {
                focusMainWindow(windowTrigger)
            }
        }
    }

    abstract fun saveCurrentActiveAppInfo()

    abstract suspend fun focusMainWindow(windowTrigger: WindowTrigger)

    fun hideSearchWindow() {
        hideBubbleWindow()
        _searchWindowInfo.value = _searchWindowInfo.value.copy(show = false)
    }

    fun switchSearchWindow(
        windowTrigger: WindowTrigger,
        saveCurrentActiveAppInfo: () -> Unit,
    ) {
        val currentShow = _searchWindowInfo.value.show
        if (currentShow) {
            hideSearchWindow()
        } else {
            saveCurrentActiveAppInfo()
            _searchWindowInfo.value =
                _searchWindowInfo.value.copy(
                    show = true,
                    state = appSize.getSearchWindowState(false),
                    trigger = windowTrigger,
                )
        }
    }

    fun showSearchWindow(windowTrigger: WindowTrigger) {
        _searchWindowInfo.value =
            _searchWindowInfo.value.copy(
                show = true,
                state = appSize.getSearchWindowState(false),
                trigger = windowTrigger,
            )
    }

    abstract suspend fun focusSearchWindow(windowTrigger: WindowTrigger)

    fun switchAlwaysOnTopMainWindow() {
        _alwaysOnTopMainWindow.value = !_alwaysOnTopMainWindow.value
    }

    abstract fun getCurrentActiveAppName(): String?

    abstract fun getRunningAppNames(): List<String>

    abstract suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean = { false })

    abstract suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean = { false },
    )

    abstract fun getPrevAppName(): Flow<String?>

    protected open fun onMainComposeWindowChanged(window: ComposeWindow?) {}

    protected open fun onSearchComposeWindowChanged(window: ComposeWindow?) {}

    protected open fun onBubbleComposeWindowChanged(window: ComposeWindow?) {}
}
