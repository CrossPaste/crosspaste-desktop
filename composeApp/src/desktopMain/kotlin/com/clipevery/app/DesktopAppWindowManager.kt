package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.clipevery.listener.ShortcutKeys
import com.clipevery.platform.currentPlatform
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
import kotlin.random.Random
import kotlin.reflect.KClass

class DesktopAppWindowManager(private val lazyShortcutKeys: Lazy<ShortcutKeys>) : AppWindowManager {

    companion object {
        const val MAIN_WINDOW_TITLE: String = "Clipevery"

        const val SEARCH_WINDOW_TITLE: String = "Clipevery Search"

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

    private val windowManager: WindowManager by lazy {
        create(lazyShortcutKeys)
    }

    private var startRefreshNumber: Int = 0

    private var refreshTokenJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override var showMainWindow by mutableStateOf(false)

    override var mainWindowPosition by mutableStateOf<WindowPosition>(WindowPosition.PlatformDefault)

    private var mainWindowActionTime = System.currentTimeMillis()

    override val mainWindowDpSize = DpSize(width = 460.dp, height = 710.dp)

    override var showMainDialog by mutableStateOf(false)

    override var showSearchWindow by mutableStateOf(false)

    override val searchWindowPosition: WindowPosition by mutableStateOf(WindowPosition.Aligned(Alignment.Center))

    override val searchWindowDpSize = DpSize(width = 800.dp, height = 520.dp)

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

    override fun activeMainWindow() {
        if (currentPlatform.isWindows()) {
            val currentTimeMillis = System.currentTimeMillis()
            val fastClick = currentTimeMillis - mainWindowActionTime < 500
            mainWindowActionTime = currentTimeMillis
            if (fastClick) {
                return
            }
        }
        showMainWindow = true
        runBlocking {
            windowManager.bringToFront(MAIN_WINDOW_TITLE)
        }
    }

    override suspend fun activeSearchWindow() {
        showSearchWindow = true
        windowManager.bringToFront(SEARCH_WINDOW_TITLE)
    }

    override fun unActiveMainWindow() {
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

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        if (showSearchWindow) {
            withContext(ioDispatcher) {
                val toPaste = preparePaste()
                windowManager.bringToBack(SEARCH_WINDOW_TITLE, toPaste)
            }
            showSearchWindow = false
        }
    }
}
