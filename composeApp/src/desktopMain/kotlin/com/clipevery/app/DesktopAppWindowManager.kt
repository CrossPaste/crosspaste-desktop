package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.os.windows.api.User32
import com.clipevery.platform.currentPlatform
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

object DesktopAppWindowManager : AppWindowManager {

    private val logger = KotlinLogging.logger {}

    private val currentPlatform = currentPlatform()

    private var startRefreshNumber: Int = 0

    private var refreshTokenJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override var showMainWindow by mutableStateOf(false)

    override val mainWindowTitle: String = "Clipevery"

    override val mainWindowDpSize = DpSize(width = 460.dp, height = 710.dp)

    override var showSearchWindow by mutableStateOf(true)

    override val searchWindowTitle: String = "Clipevery Search"

    override val searchWindowDpSize = DpSize(width = 800.dp, height = 600.dp)

    override var showToken by mutableStateOf(false)

    override var token by mutableStateOf(charArrayOf('0', '0', '0', '0', '0', '0'))

    private var prevAppName: String? = null

    private fun refreshToken() {
        token = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
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

    override fun activeMainWindow() {
        if (currentPlatform().isMacos()) {
            logger.info { "bringToFront Clipevery" }
            MacosApi.INSTANCE.bringToFront(mainWindowTitle)
        }
        showMainWindow = true
    }

    override fun activeSearchWindow() {
        if (currentPlatform.isMacos()) {
            prevAppName = MacosApi.INSTANCE.bringToFront(searchWindowTitle)
        } else if (currentPlatform.isWindows()) {
            val prevAppId: Int = User32.bringToFrontAndReturnPreviousAppId(searchWindowTitle)
            if (prevAppId > 0) {
                prevAppName = prevAppId.toString()
            }
        } else if (currentPlatform.isLinux()) {
            // todo linux
        }
        showSearchWindow = true
        logger.info { "save prevAppName is ${prevAppName ?: "null"}" }
    }

    override fun unActiveMainWindow() {
        logger.info { "${currentPlatform.name} bringToBack Clipevery" }
        if (currentPlatform.isMacos()) {
            MacosApi.INSTANCE.bringToBack(mainWindowTitle)
        } else if (currentPlatform.isWindows()) {
            User32.hideWindowByTitle(mainWindowTitle)
        } else if (currentPlatform.isLinux()) {
            // todo linux
        }
        showMainWindow = false
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        if (showSearchWindow) {
            showSearchWindow = false
            val toPaste = preparePaste()
            prevAppName?.let {
                logger.info { "${currentPlatform.name} unActiveWindow return to app $it" }
                if (currentPlatform.isMacos()) {
                    MacosApi.INSTANCE.activeApp(it, toPaste)
                } else if (currentPlatform.isWindows()) {
                    User32.activateAppAndPaste(it.toInt(), toPaste)
                } else if (currentPlatform.isLinux()) {
                    // todo linux
                }
            }
        }
    }
}
