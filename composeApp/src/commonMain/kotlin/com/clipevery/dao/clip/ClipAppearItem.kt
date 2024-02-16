package com.clipevery.dao.clip

interface ClipAppearItem {

    fun getIdentifiers(): List<String>

    fun getClipType(): Int

    fun getSearchContent(): String?

    var md5: String

    fun update(data: Any, md5: String)

    fun clear()
}

fun sortClipAppearItems(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem> {
    return clipAppearItems.sortedBy { it.getClipType() }
}
