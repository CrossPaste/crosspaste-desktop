package com.crosspaste.utils

actual fun getSystemProperty(): SystemProperty = DesktopSystemProperty

// Delegate all System.getProperty methods to DesktopSystemProperty,
// for convenient mocking in unit tests.
object DesktopSystemProperty : SystemProperty {

    override fun getOption(key: String): String? = System.getProperty(key)

    override fun get(key: String): String = System.getProperty(key)

    override fun get(
        key: String,
        default: String,
    ): String = System.getProperty(key, default)

    override fun set(
        key: String,
        value: String,
    ) {
        System.setProperty(key, value)
    }
}
