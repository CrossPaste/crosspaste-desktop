package com.clipevery.net

import java.nio.file.Path

interface FaviconLoader {

    fun getFaviconPath(url: String): Path?
}
