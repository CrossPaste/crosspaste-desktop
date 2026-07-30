package com.crosspaste.app

import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

abstract class AppTokenService : AppTokenApi {

    private val scope = namedScope(ioDispatcher, "AppTokenService")

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
                            delay(300.milliseconds)
                        }
                    }
                } else {
                    _refreshProgress.value = 0f
                }
            }
        }
    }

    abstract fun preShowToken()

    abstract fun preShowPairingCode()

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
                decrementRefreshLocked()
            }
        }
    }

    override fun releaseVerifier(
        appInstanceId: String,
        hideToken: Boolean,
    ) {
        // Remove the verifier SYNCHRONOUSLY (same semantics as
        // removePendingVerifier): callers such as the v2 exchange route release
        // a superseded peer and immediately re-add it, so deferring the removal
        // into the async block would erase the re-added verifier. The atomic
        // getAndUpdate makes concurrent releases race safely — only the caller
        // that actually removed the entry gets to decrement.
        val hadVerifier =
            appInstanceId in
                _pendingVerifiers.getAndUpdate { currentSet ->
                    currentSet - appInstanceId
                }
        scope.launch {
            lock.withLock {
                if (hideToken) {
                    _showToken.value = false
                }
                // Decrement only for the verifier we actually removed — an
                // already-released verifier must not consume a count owned by
                // another pairing in flight.
                if (hadVerifier) {
                    decrementRefreshLocked()
                }
            }
        }
    }

    private fun decrementRefreshLocked() {
        if (refreshCounter > 0) {
            refreshCounter -= 1
        }
        if (refreshCounter == 0) {
            _refresh.value = false
            _showToken.value = false
            _sasMode = false
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

    override fun showPairingCode() {
        preShowPairingCode()
    }

    private fun refreshToken() {
        if (!_sasMode) {
            _token.value = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
        }
        _refreshProgress.value = 0.0f
    }
}
