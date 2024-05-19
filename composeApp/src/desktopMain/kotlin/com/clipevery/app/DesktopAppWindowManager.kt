package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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

object DesktopAppWindowManager : AppWindowManager {

    private val logger: KLogger = KotlinLogging.logger {}

    private val currentPlatform = currentPlatform()

    private val windowManager: WindowManager =
        run {
            when {
                currentPlatform.isMacos() -> MacWindowManager()
                currentPlatform.isWindows() -> WinWindowManager()
                currentPlatform.isLinux() -> LinuxWindowManager()
                else -> throw IllegalStateException("Unsupported platform: $currentPlatform")
            }
        }

    private var startRefreshNumber: Int = 0

    private var refreshTokenJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override var showMainWindow by mutableStateOf(false)

    override val mainWindowTitle: String = "Clipevery"

    private var mainWindowActionTime = System.currentTimeMillis()

    override val mainWindowDpSize = DpSize(width = 460.dp, height = 710.dp)

    override var showSearchWindow by mutableStateOf(false)

    override val searchWindowTitle: String = "Clipevery Search"

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
            windowManager.bringToFront(mainWindowTitle)
        }
    }

    override suspend fun activeSearchWindow() {
        showSearchWindow = true
        windowManager.bringToFront(searchWindowTitle)
    }

    override fun unActiveMainWindow() {
        runBlocking {
            withContext(ioDispatcher) {
                windowManager.bringToBack(mainWindowTitle, false)
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
                windowManager.bringToBack(searchWindowTitle, toPaste)
            }
            showSearchWindow = false
        }
    }
}
