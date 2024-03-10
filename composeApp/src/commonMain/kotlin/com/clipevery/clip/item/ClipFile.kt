package com.clipevery.clip.item

import java.nio.file.Path

interface ClipFile {

    fun getFilePath(): Path

    fun getMd5(): String
}