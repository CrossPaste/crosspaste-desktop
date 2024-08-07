package com.crosspaste.path

import okio.Path

interface AppPathProvider {

    val userHome: Path

    val pasteAppPath: Path

    val pasteAppJarPath: Path

    val pasteUserPath: Path
}
