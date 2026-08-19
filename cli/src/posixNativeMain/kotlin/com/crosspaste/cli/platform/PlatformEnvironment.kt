package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * The single environment accessor for values that may carry non-ASCII content
 * (paths, editor commands). The narrow getenv is correct on POSIX, where the
 * environment is byte-oriented; the Windows twin must go through the
 * wide-character CRT instead.
 */
@OptIn(ExperimentalForeignApi::class)
fun readPlatformEnv(name: String): String? = getenv(name)?.toKString()
