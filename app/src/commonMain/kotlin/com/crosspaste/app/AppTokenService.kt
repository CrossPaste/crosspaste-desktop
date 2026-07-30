package com.crosspaste.app

import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

abstract class AppTokenService : AppTokenApi {

    private val scope = namedScope(ioDispatcher, "AppTokenService")

    // Every state mutation (verifier set, refresh counter, overlay visibility,
    // SAS mode) runs on ONE consumer coroutine in submission order. Callers
    // enqueue synchronously (trySend on an UNLIMITED channel never fails), so
    // each caller's own call sequence is executed in that order — a verifier
    // release can never be processed before the startRefresh that funded it,
    // which is what previously allowed an orphaned count to keep the refresh
    // loop alive forever (#4684 / #4690 review).
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)

    private val _refreshProgress = MutableStateFlow(0f)

    override val refreshProgress: StateFlow<Float> = _refreshProgress.asStateFlow()

    private val _refresh = MutableStateFlow(false)

    override val refresh: StateFlow<Boolean> = _refresh.asStateFlow()

    // Confined to the command consumer coroutine.
    private var refreshCounter = 0

    private val _showToken = MutableStateFlow(false)

    override val showToken: StateFlow<Boolean> = _showToken.asStateFlow()

    private val _pendingVerifiers = MutableStateFlow<Set<String>>(emptySet())

    override val pendingVerifiers: StateFlow<Set<String>> = _pendingVerifiers.asStateFlow()

    // Written only on the command consumer coroutine; also read by the refresh
    // loop (a stale read at worst regenerates one random token — benign).
    private var _sasMode = false

    private val _token = MutableStateFlow(charArrayOf('0', '0', '0', '0', '0', '0'))

    override val token: StateFlow<CharArray> = _token.asStateFlow()

    init {
        scope.launch {
            for (command in commands) {
                command()
            }
        }
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

    private fun submit(command: () -> Unit) {
        commands.trySend(command)
    }

    override fun sameToken(token: Int): Boolean =
        token ==
            this.token.value
                .concatToString()
                .toInt()

    override fun setSASToken(sas: Int) {
        submit {
            val padded = sas.toString().padStart(6, '0')
            _token.value = padded.toCharArray()
            _sasMode = true
        }
    }

    override fun startRefresh(showToken: Boolean) {
        submit {
            if (showToken) {
                _showToken.value = true
                preShowToken()
            }
            _refresh.value = true
            refreshCounter += 1
        }
    }

    override fun stopRefresh(hideToken: Boolean) {
        submit {
            if (hideToken) {
                _showToken.value = false
            }
            decrementRefresh()
        }
    }

    override fun releaseVerifier(
        appInstanceId: String,
        hideToken: Boolean,
    ) {
        submit {
            val hadVerifier = appInstanceId in _pendingVerifiers.value
            _pendingVerifiers.value -= appInstanceId
            if (hideToken) {
                _showToken.value = false
            }
            // Decrement only for the verifier we actually removed — an
            // already-released verifier must not consume a count owned by
            // another pairing in flight.
            if (hadVerifier) {
                decrementRefresh()
            }
        }
    }

    override fun addPendingVerifier(appInstanceId: String) {
        submit {
            _pendingVerifiers.value += appInstanceId
        }
    }

    override fun removePendingVerifier(appInstanceId: String) {
        submit {
            _pendingVerifiers.value -= appInstanceId
        }
    }

    override fun showPairingCode() {
        preShowPairingCode()
    }

    // Runs on the command consumer coroutine only.
    private fun decrementRefresh() {
        if (refreshCounter > 0) {
            refreshCounter -= 1
        }
        if (refreshCounter == 0) {
            _refresh.value = false
            _showToken.value = false
            _sasMode = false
        }
    }

    private fun refreshToken() {
        if (!_sasMode) {
            _token.value = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
        }
        _refreshProgress.value = 0.0f
    }
}
