package com.crosspaste.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.experimental.ExperimentalNativeApi

actual fun getSystemProperty(): SystemProperty = NativeSystemProperty

object NativeSystemProperty : SystemProperty {

    private val properties = mutableMapOf<String, String>()

    init {
        initDefaultProperties()
    }

    @OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
    private fun initDefaultProperties() {
        // os.name
        properties["os.name"] = Platform.osFamily.name

        // user.home
        getenv("HOME")?.toKString()?.let {
            properties["user.home"] = it
        }

        // user.name
        getenv("USER")?.toKString()?.let {
            properties["user.name"] = it
        }

        // user.dir
        getenv("PWD")?.toKString()?.let {
            properties["user.dir"] = it
        }

        // java.io.tmpdir equivalent
        val tmpDir =
            getenv("TMPDIR")?.toKString()
                ?: getenv("TMP")?.toKString()
                ?: "/tmp"
        properties["java.io.tmpdir"] = tmpDir
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun getOption(key: String): String? = properties[key] ?: getenv(key)?.toKString()

    override fun get(key: String): String = getOption(key) ?: error("System property '$key' not found")

    override fun get(
        key: String,
        default: String,
    ): String = getOption(key) ?: default

    override fun set(
        key: String,
        value: String,
    ) {
        properties[key] = value
    }
}
