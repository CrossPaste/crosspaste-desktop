package com.crosspaste.paste.item

import okio.Path

interface PasteFile {

    fun getFilePath(): Path

    fun getHash(): String
}

class PasteFileImpl(private val path: Path, private val hash: String) : PasteFile {

    override fun getFilePath(): Path {
        return path
    }

    override fun getHash(): String {
        return hash
    }
}
