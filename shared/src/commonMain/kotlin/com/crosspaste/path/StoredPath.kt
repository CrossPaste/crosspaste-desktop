package com.crosspaste.path

import com.crosspaste.config.canonicalizeOrSelf
import com.crosspaste.utils.getPlatformUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toPath

private const val APP_HOME = "{appHome}"

private val logger = KotlinLogging.logger {}

/**
 * The form an absolute path is stored in. A path under the relocatable app home
 * (the iOS app container, whose path changes across updates and restores) is
 * stored as `{appHome}/...`; any other path is stored as is.
 */
fun encodeStoredPath(path: String): String {
    val appHome = getPlatformUtils().getRelocatableAppHome() ?: return path
    return encodeStoredPath(path, appHome).also { stored ->
        if (!stored.startsWith(APP_HOME)) {
            logger.warn { "Storing $path as an absolute path: it is outside the app home $appHome" }
        }
    }
}

/** Inverse of [encodeStoredPath], against the app home of this launch. */
fun decodeStoredPath(storedPath: String): Path =
    decodeStoredPath(storedPath, getPlatformUtils().getRelocatableAppHome())

/**
 * Both sides are canonicalized before comparing: iOS hands out the container both
 * as `/var/...` and as `/private/var/...`, and a prefix mismatch would silently
 * store the absolute path again.
 */
fun encodeStoredPath(
    path: String,
    appHome: Path?,
): String {
    appHome ?: return path
    val relative =
        runCatching {
            canonicalizeOrSelf(path.toPath(normalize = true)).relativeTo(canonicalizeOrSelf(appHome.normalized()))
        }.getOrNull()
    return when {
        relative == null || relative.segments.firstOrNull() == ".." -> path
        relative.segments.all { it == "." } -> APP_HOME
        else -> "$APP_HOME/$relative"
    }
}

fun decodeStoredPath(
    storedPath: String,
    appHome: Path?,
): Path {
    if (!storedPath.startsWith(APP_HOME) || appHome == null) return storedPath.toPath()
    val relative = storedPath.removePrefix(APP_HOME).trimStart('/')
    return if (relative.isEmpty()) appHome else appHome.resolve(relative)
}
