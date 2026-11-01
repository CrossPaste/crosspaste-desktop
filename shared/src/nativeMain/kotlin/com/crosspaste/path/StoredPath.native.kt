package com.crosspaste.path

import okio.Path
import okio.Path.Companion.toPath

actual fun encodeStoredPath(path: String): String = path

actual fun decodeStoredPath(storedPath: String): Path = storedPath.toPath()
