package com.crosspaste.realm.paste

data class PasteResourceInfo(
    val pasteCount: Long,
    val pasteSize: Long,
    val textCount: Long,
    val textSize: Long,
    val urlCount: Long,
    val urlSize: Long,
    val htmlCount: Long,
    val htmlSize: Long,
    val imageCount: Long,
    val imageSize: Long,
    val fileCount: Long,
    val fileSize: Long,
)
