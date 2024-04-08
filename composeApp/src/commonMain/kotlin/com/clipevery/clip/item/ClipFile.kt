package com.clipevery.clip.item

import java.nio.file.Path

interface ClipFile {

    fun getFilePath(): Path

    fun getMd5(): String
}

class ClipFileImpl(private val path: Path, private val md5: String) : ClipFile {

    override fun getFilePath(): Path {
        return path
    }

    override fun getMd5(): String {
        return md5
    }
}
