package com.crosspaste.net

import okio.Path

interface FaviconLoader {

    fun getFaviconPath(url: String): Path?
}
