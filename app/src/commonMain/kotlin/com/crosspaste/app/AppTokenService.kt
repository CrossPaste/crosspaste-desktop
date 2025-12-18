package com.crosspaste.app

import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

abstract class AppTokenService : AppTokenApi {

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _showTokenProgress = MutableStateFlow(0f)

    override val showTokenProgression: StateFlow<Float> = _showTokenProgress.asStateFlow()

    private val _showToken = MutableStateFlow(false)

    override val showToken: StateFlow<Boolean> = _showToken.asStateFlow()

    private val _token = MutableStateFlow(charArrayOf('0', '0', '0', '0', '0', '0'))

    override val token: StateFlow<CharArray> = _token.asStateFlow()

    init {
        scope.launch {
            showToken.collectLatest { isShowing ->
                if (isShowing) {
                    while (isActive) {
                        refreshToken()
                        val totalSteps = 100
                        for (i in 0..totalSteps) {
                            _showTokenProgress.value = i / totalSteps.toFloat()
                            delay(300)
                        }
                    }
                } else {
                    _showTokenProgress.value = 0f
                }
            }
        }
    }

    abstract fun preShowToken()

    override fun sameToken(token: Int): Boolean =
        token ==
            this.token.value
                .concatToString()
                .toInt()

    override fun toShowToken(showView: Boolean) {
        if (showView) {
            preShowToken()
        }
        _showToken.value = true
    }

    override fun toHideToken() {
        _showToken.value = false
    }

    private fun refreshToken() {
        _token.value = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
        _showTokenProgress.value = 0.0f
    }
}
