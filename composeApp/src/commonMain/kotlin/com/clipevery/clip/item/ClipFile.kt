package com.clipevery.clip.item

import java.nio.file.Path

interface ClipFile {

    var isFile: Boolean

    fun getFilePath(): Path

    fun getExtension(): String
}