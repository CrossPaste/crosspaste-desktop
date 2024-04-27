package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.clipevery.platform.currentPlatform
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

object DesktopAppWindowManager : AppWindowManager {

    private val windowManager: WindowManager =
        run {
            val currentPlatform = currentPlatform()
            when {
                currentPlatform.isMacos() -> MacWindowManager()
                currentPlatform.isWindows() -> WinWindowManager()
                else -> throw IllegalStateException("Unsupported platform: $currentPlatform")
            }
        }

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
            windowManager.bringToBack(mainWindowTitle, false)
        }
        showMainWindow = false
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        if (showSearchWindow) {
            showSearchWindow = false
            val toPaste = preparePaste()
            windowManager.bringToBack(searchWindowTitle, toPaste)
        }
    }
}
