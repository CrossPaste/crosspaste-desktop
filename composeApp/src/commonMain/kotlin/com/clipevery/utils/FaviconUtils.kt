package com.clipevery.utils

import java.nio.file.Path

expect fun getFaviconUtils(): FaviconUtils

interface FaviconUtils {

    fun getFaviconPath(url: String): Path?
}
