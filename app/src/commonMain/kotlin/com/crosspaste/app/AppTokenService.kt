package com.crosspaste.app

import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

abstract class AppTokenService : AppTokenApi {

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val lock = Mutex()

    private val _refreshProgress = MutableStateFlow(0f)

    override val refreshProgress: StateFlow<Float> = _refreshProgress.asStateFlow()

    private val _refresh = MutableStateFlow(false)

    override val refresh: StateFlow<Boolean> = _refresh.asStateFlow()

    private var refreshCounter = 0

    private val _showToken = MutableStateFlow(false)

    override val showToken: StateFlow<Boolean> = _showToken.asStateFlow()

    private val _pendingVerifiers = MutableStateFlow<Set<String>>(emptySet())

    override val pendingVerifiers: StateFlow<Set<String>> = _pendingVerifiers.asStateFlow()

    private var _sasMode = false

    private val _token = MutableStateFlow(charArrayOf('0', '0', '0', '0', '0', '0'))

    override val token: StateFlow<CharArray> = _token.asStateFlow()

    init {
        scope.launch {
            refresh.collectLatest { isShowing ->
                if (isShowing) {
                    while (isActive) {
                        refreshToken()
                        val totalSteps = 100
                        for (i in 0..totalSteps) {
                            _refreshProgress.value = i / totalSteps.toFloat()
                            delay(300)
                        }
                    }
                } else {
                    _refreshProgress.value = 0f
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

    override fun setSASToken(sas: Int) {
        val padded = sas.toString().padStart(6, '0')
        _token.value = padded.toCharArray()
        _sasMode = true
    }

    override fun startRefresh(showToken: Boolean) {
        scope.launch {
            lock.withLock {
                if (showToken) {
                    _showToken.value = true
                    preShowToken()
                }
                _refresh.value = true
                refreshCounter += 1
            }
        }
    }

    override fun stopRefresh(hideToken: Boolean) {
        scope.launch {
            lock.withLock {
                if (hideToken) {
                    _showToken.value = false
                }
                if (refreshCounter > 0) {
                    refreshCounter -= 1
                }
                if (refreshCounter == 0) {
                    _refresh.value = false
                    _showToken.value = false
                    _sasMode = false
                }
            }
        }
    }

    override fun addPendingVerifier(appInstanceId: String) {
        _pendingVerifiers.update { currentSet ->
            currentSet + appInstanceId
        }
    }

    override fun removePendingVerifier(appInstanceId: String) {
        _pendingVerifiers.update { currentSet ->
            currentSet - appInstanceId
        }
    }

    private fun refreshToken() {
        if (!_sasMode) {
            _token.value = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
        }
        _refreshProgress.value = 0.0f
    }
}
