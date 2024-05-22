package com.clipevery.utils

expect fun getSystemProperty(): SystemProperty

interface SystemProperty {

    fun getOption(key: String): String?

    fun get(key: String): String

    fun get(
        key: String,
        default: String,
    ): String
}
