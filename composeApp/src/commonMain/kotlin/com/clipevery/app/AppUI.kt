package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import kotlin.random.Random

class AppUI(val width: Dp, val height: Dp) {

    var showWindow by mutableStateOf(false)

    var showToken by mutableStateOf(false)

    var token by mutableStateOf(charArrayOf('0', '0', '0', '0', '0', '0'))

    fun refreshToken() {
        token = CharArray(6) { (Random.nextInt(10) + '0'.code).toChar() }
    }

}
