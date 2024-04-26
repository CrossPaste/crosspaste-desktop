package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.clipevery.os.macos.api.MacosApi
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
        val currentPlatform = currentPlatform()
        if (currentPlatform.isMacos()) {
            prevAppName = MacosApi.INSTANCE.bringToFront(searchWindowTitle)
        } else if (currentPlatform.isWindows()) {
            // todo windows
        } else if (currentPlatform.isLinux()) {
            // todo linux
        }
        showSearchWindow = true
        logger.info { "save prevAppName is ${prevAppName ?: "null"}" }
    }

    override fun unActiveMainWindow() {
        if (currentPlatform().isMacos()) {
            logger.info { "bringToBack Clipevery" }
            MacosApi.INSTANCE.bringToBack(mainWindowTitle)
        }
        showMainWindow = false
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        if (showSearchWindow) {
            showSearchWindow = false
            val toPaste = preparePaste()
            prevAppName?.let {
                MacosApi.INSTANCE.activeApp(it, toPaste)
                logger.info { "unActiveWindow return to app $it" }
            }
        }
    }
}
