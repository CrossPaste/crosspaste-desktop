package com.crosspaste.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class DesktopAppTokenService(
    private val appWindowManager: DesktopAppWindowManager,
) : AppTokenService {

    private var startRefreshNumber: Int = 0

    private var refreshTokenJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    override var showTokenProgress: Float by mutableStateOf(0.0f)

    override var showToken by mutableStateOf(false)

    override var token by mutableStateOf(charArrayOf('0', '0', '0', '0', '0', '0'))

    override fun toShowToken() {
        appWindowManager.setShowMainWindow(true)
        showToken = true
    }

    private suspend fun refreshToken() {
        withContext(mainDispatcher) {
            token = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
            showTokenProgress = 0.0f
        }
    }

    @Synchronized
    override fun startRefreshToken() {
        if (startRefreshNumber++ == 0) {
            refreshTokenJob =
                scope.launch(CoroutineName("RefreshToken")) {
                    while (isActive) {
                        refreshToken()
                        while (showTokenProgress < 0.99f) {
                            withContext(mainDispatcher) {
                                showTokenProgress += 0.01f
                            }
                            delay(300)
                        }
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
}
