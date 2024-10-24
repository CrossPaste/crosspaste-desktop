package com.crosspaste.app

import com.crosspaste.utils.createPlatformLock
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

open class AppTokenService {

    private val lock = createPlatformLock()

    private val scope = CoroutineScope(ioDispatcher)

    private var enableRefresh = false

    private var refreshTokenJob: Job? = null

    private val _showTokenProgress = MutableStateFlow(0f)

    val showTokenProgression: StateFlow<Float> = _showTokenProgress.asStateFlow()

    private val _showToken = MutableStateFlow(false)

    val showToken: StateFlow<Boolean> = _showToken.asStateFlow()

    private val _token = MutableStateFlow(charArrayOf('0', '0', '0', '0', '0', '0'))

    val token: StateFlow<CharArray> = _token.asStateFlow()

    open fun preShowToken() {
    }

    fun sameToken(token: Int): Boolean {
        return token == this.token.value.concatToString().toInt()
    }

    fun toShowToken() {
        preShowToken()
        _showToken.value = true
    }

    fun toHideToken() {
        _showToken.value = false
    }

    private fun refreshToken() {
        _token.value = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
        _showTokenProgress.value = 0.0f
    }

    fun startRefreshToken() {
        lock.withLock {
            if (enableRefresh) return@withLock
            enableRefresh = true
            refreshTokenJob =
                scope.launch {
                    while (true) {
                        refreshToken()
                        while (_showTokenProgress.value < 0.99f) {
                            _showTokenProgress.value += 0.01f
                            delay(300)
                        }
                    }
                }
        }
    }

    fun stopRefreshToken() {
        lock.withLock {
            if (!enableRefresh) return@withLock
            enableRefresh = false
            refreshTokenJob?.cancel()
        }
    }
}
