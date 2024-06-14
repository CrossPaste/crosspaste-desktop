package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.clipevery.listen.ActiveGraphicsDevice
import com.clipevery.listener.ShortcutKeys
import com.clipevery.platform.currentPlatform
import com.clipevery.utils.DesktopControlUtils.blockDebounce
import com.clipevery.utils.DesktopControlUtils.debounce
import com.clipevery.utils.Memoize
import com.clipevery.utils.ioDispatcher
import com.clipevery.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Rectangle
import kotlin.random.Random
import kotlin.reflect.KClass

class DesktopAppWindowManager(
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
    debounceDelay: Long = 100L,
) : AppWindowManager {

    companion object {
        const val MAIN_WINDOW_TITLE: String = "Clipevery"

        const val SEARCH_WINDOW_TITLE: String = "Clipevery Search"

        // only use in Windows
        const val MENU_WINDOW_TITLE: String = "Clipevery Menu"

        val currentPlatform = currentPlatform()

        @Suppress("UNCHECKED_CAST")
        fun create(lazyShortcutKeys: Lazy<ShortcutKeys>): WindowManager {
            if (AppEnv.isTest()) {
                val kClass = Class.forName("com.clipevery.app.TestWindowManager").kotlin as KClass<WindowManager>
                return kClass.objectInstance ?: throw IllegalStateException("Expected a singleton instance")
            }
            val shortcutKeys = lazyShortcutKeys.value
            return when {
                currentPlatform.isMacos() -> MacWindowManager(shortcutKeys)
                currentPlatform.isWindows() -> WinWindowManager(shortcutKeys)
                currentPlatform.isLinux() -> LinuxWindowManager(shortcutKeys)
                else -> throw IllegalStateException("Unsupported platform: $currentPlatform")
            }
        }
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override val windowManager: WindowManager by lazy {
        create(lazyShortcutKeys)
    }

    private var startRefreshNumber: Int = 0

    private var refreshTokenJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override var showMainWindow by mutableStateOf(false)

    override var mainWindowState: WindowState by mutableStateOf(
        WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.PlatformDefault,
            size = DpSize(width = 460.dp, height = 710.dp),
        ),
    )

    private var mainWindowActionTime = System.currentTimeMillis()

    override var showMainDialog by mutableStateOf(false)

    override var showSearchWindow by mutableStateOf(false)

    override var searchWindowState: WindowState by mutableStateOf(
        WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(width = 800.dp, height = 520.dp),
        ),
    )

    override var focusRequester = FocusRequester()

    override val searchWindowDetailViewDpSize = DpSize(width = 500.dp, height = 240.dp)

    override var showToken by mutableStateOf(false)

    override var token by mutableStateOf(charArrayOf('0', '0', '0', '0', '0', '0'))

    private suspend fun refreshToken() {
        withContext(mainDispatcher) {
            token = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
        }
    }

    @Synchronized
    override fun startRefreshToken() {
        if (startRefreshNumber++ == 0) {
            refreshTokenJob =
                scope.launch(CoroutineName("RefreshToken")) {
                    while (isActive) {
                        refreshToken()
                        delay(30000)
                    }
                }
        }
    }

    @Synchronized
    override fun stopRefreshToken() {
        startRefreshNumber -= 1
        if (startRefreshNumber == 0) {
            refreshTokenJob?.cancel()
        }
    }

    override fun getPrevAppName(): String? {
        return windowManager.getPrevAppName()
    }

    override suspend fun toPaste() {
        windowManager.toPaste()
    }

    override fun getCurrentActiveAppName(): String? {
        return try {
            windowManager.getCurrentActiveAppName()
        } catch (e: Exception) {
            logger.error(e) { "getCurrentActiveAppName fail" }
            null
        }
    }

    private val debounceActiveMainWindow =
        blockDebounce(
            delay = debounceDelay,
        ) {
            if (currentPlatform.isWindows()) {
                val currentTimeMillis = System.currentTimeMillis()
                val fastClick = currentTimeMillis - mainWindowActionTime < 500
                mainWindowActionTime = currentTimeMillis
                if (fastClick) {
                    return@blockDebounce
                }
            }
            showMainWindow = true
            runBlocking {
                windowManager.bringToFront(MAIN_WINDOW_TITLE)
            }
        }

    override fun activeMainWindow() {
        debounceActiveMainWindow()
    }

    private val calPosition: (Rectangle) -> WindowPosition =
        Memoize.memoize { bounds ->
            val windowSize = searchWindowState.size
            WindowPosition(
                x = (bounds.x.dp + ((bounds.width.dp - windowSize.width) / 2)),
                y = (bounds.y.dp + ((bounds.height.dp - windowSize.height) / 2)),
            )
        }

    private val debounceActiveSearchWindow =
        debounce(
            delay = debounceDelay,
        ) {
            showSearchWindow = true

            activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
                searchWindowState.position = calPosition(graphicsDevice.defaultConfiguration.bounds)
            }

            windowManager.bringToFront(SEARCH_WINDOW_TITLE)

            if (!AppEnv.isTest()) {
                delay(500)
                focusRequester.requestFocus()
            }
        }

    override suspend fun activeSearchWindow() {
        debounceActiveSearchWindow()
    }

    private val debounceUnActiveMainWindow =
        blockDebounce(
            delay = debounceDelay,
        ) {
            runBlocking {
                withContext(ioDispatcher) {
                    windowManager.bringToBack(MAIN_WINDOW_TITLE, false)
                }
            }
            if (currentPlatform.isWindows()) {
                mainWindowActionTime = System.currentTimeMillis()
            }
            showMainWindow = false
        }

    override fun unActiveMainWindow() {
        debounceUnActiveMainWindow()
    }

    private val debounceUnActiveSearchWindow: suspend (suspend () -> Boolean) -> Unit =
        debounce(
            delay = debounceDelay,
        ) { preparePaste ->
            withContext(ioDispatcher) {
                val toPaste = preparePaste()
                windowManager.bringToBack(SEARCH_WINDOW_TITLE, toPaste)
            }
            showSearchWindow = false

            if (!AppEnv.isTest()) {
                focusRequester.freeFocus()
            }
        }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        debounceUnActiveSearchWindow(preparePaste)
    }
}
