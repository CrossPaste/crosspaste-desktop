package com.clipevery.app

import kotlin.random.Random

class AppToken {

    private val token: CharArray = charArrayOf('0', '0', '1', '0', '2', '4')

    fun generateToken(): CharArray {
        for (i in token.indices) {
            token[i] = (Random.nextInt(10) + '0'.code).toChar()
        }
        return token
    }
}
