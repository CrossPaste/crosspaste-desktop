package com.crosspaste.utils

expect fun getSystemProperty(): SystemProperty

interface SystemProperty {

    fun getOption(key: String): String?

    fun get(key: String): String

    fun get(
        key: String,
        default: String,
    ): String

    fun set(
        key: String,
        value: String,
    )
}
