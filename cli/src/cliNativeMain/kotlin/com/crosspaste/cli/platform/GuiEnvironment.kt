package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.experimental.ExperimentalNativeApi

/**
 * macOS and Windows always have a graphical session the desktop app can
 * attach to; on Linux the CLI may be running on a headless server where
 * launching the desktop app is impossible (headless daemon support is a
 * separate future deliverable, design P6).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
fun isGuiEnvironment(): Boolean =
    when (Platform.osFamily) {
        OsFamily.MACOSX, OsFamily.WINDOWS -> true
        OsFamily.LINUX -> hasEnv("DISPLAY") || hasEnv("WAYLAND_DISPLAY")
        else -> false
    }

@OptIn(ExperimentalForeignApi::class)
private fun hasEnv(name: String): Boolean = getenv(name)?.toKString()?.isNotEmpty() == true
