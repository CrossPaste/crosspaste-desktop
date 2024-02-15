package com.clipevery.dao.clip

interface ClipAppearItem {

    fun getIdentifiers(): List<String>

    fun getClipType(): Int

    fun getSearchContent(): String?

    fun getMD5(): String

    fun update(data: Any, md5: String)

    fun clear()
}
