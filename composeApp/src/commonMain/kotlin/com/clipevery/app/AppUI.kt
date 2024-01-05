package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class AppUI(val width: Dp, val height: Dp) {

    private var startRefreshNumber: Int = 0

    private var refreshTokenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var showWindow by mutableStateOf(false)

    var showToken by mutableStateOf(false)

    var token by mutableStateOf(charArrayOf('0', '0', '0', '0', '0', '0'))

    private fun refreshToken() {
        token = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
    }

    @Synchronized
    fun startRefreshToken() {
        if (startRefreshNumber ++ == 0) {
            refreshTokenJob = scope.launch {
                while (isActive) {
                    refreshToken()
                    delay(10000)
                }
            }
        }
    }

    @Synchronized
    fun stopRefreshToken() {
        startRefreshNumber -= 1
        if (startRefreshNumber == 0) {
            refreshTokenJob?.cancel()
        }
    }

}
