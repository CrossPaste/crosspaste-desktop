package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.wcstr
import platform.posix._wgetenv

/**
 * The single environment accessor for values that may carry non-ASCII content
 * (paths, editor commands). The narrow getenv would return values re-encoded
 * through the ANSI code page, garbling non-ASCII content (e.g. USERPROFILE or
 * TEMP under a Chinese user profile); _wgetenv reads the native UTF-16
 * environment directly.
 */
@OptIn(ExperimentalForeignApi::class)
fun readPlatformEnv(name: String): String? = memScoped { _wgetenv(name.wcstr.ptr)?.toKStringFromUtf16() }
