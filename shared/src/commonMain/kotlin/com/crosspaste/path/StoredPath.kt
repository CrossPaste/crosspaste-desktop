package com.crosspaste.path

import com.crosspaste.utils.getPlatformUtils
import okio.Path
import okio.Path.Companion.toPath

private const val APP_HOME = "{appHome}"

/**
 * The form an absolute path is stored in. A path under the relocatable app home
 * (the iOS app container, whose path changes across updates and restores) is
 * stored as `{appHome}/...`; any other path is stored as is.
 */
fun encodeStoredPath(path: String): String = encodeStoredPath(path, getPlatformUtils().getRelocatableAppHome())

/** Inverse of [encodeStoredPath], against the app home of this launch. */
fun decodeStoredPath(storedPath: String): Path =
    decodeStoredPath(storedPath, getPlatformUtils().getRelocatableAppHome())

fun encodeStoredPath(
    path: String,
    appHome: Path?,
): String {
    appHome ?: return path
    val relative = runCatching { path.toPath(normalize = true).relativeTo(appHome.normalized()) }.getOrNull()
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
