package com.crosspaste.cli

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv
import kotlin.random.Random

/**
 * Runs [block] with [name] set to [value], then restores the previous value.
 * A null [value] unsets the variable for the duration of [block].
 */
@OptIn(ExperimentalForeignApi::class)
fun withEnv(
    name: String,
    value: String?,
    block: () -> Unit,
) {
    val original = getenv(name)?.toKString()
    if (value == null) {
        unsetenv(name)
    } else {
        setenv(name, value, 1)
    }
    try {
        block()
    } finally {
        if (original == null) {
            unsetenv(name)
        } else {
            setenv(name, original, 1)
        }
    }
}

/**
 * Runs [block] with `HOME` pointed at a fresh temp directory, then restores
 * the previous `HOME` even when [block] throws.
 */
@OptIn(ExperimentalForeignApi::class)
fun withIsolatedHome(block: () -> Unit) {
    val dir =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .resolve("cli-isolated-home-${Random.nextBits(31)}")
    FileSystem.SYSTEM.createDirectories(dir)
    withEnv("HOME", dir.toString(), block)
}
