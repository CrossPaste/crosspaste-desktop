package com.crosspaste.paste.item

import java.nio.file.Path

interface PasteFile {

    fun getFilePath(): Path

    fun getMd5(): String
}

class PasteFileImpl(private val path: Path, private val md5: String) : PasteFile {

    override fun getFilePath(): Path {
        return path
    }

    override fun getMd5(): String {
        return md5
    }
}
