package com.clipevery.clip.item

import java.nio.file.Path

interface ClipFiles {

    fun getFilePaths(): List<Path>

    fun getFileMd5List(): List<String>
}
