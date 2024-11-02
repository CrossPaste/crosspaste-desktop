package com.crosspaste.realm.paste

data class PasteResourceInfo(
    val pasteCount: Long,
    val pasteSize: Long,
    val textCount: Long,
    val textSize: Long,
    val colorCount: Long,
    val colorSize: Long,
    val urlCount: Long,
    val urlSize: Long,
    val htmlCount: Long,
    val htmlSize: Long,
    val rtfCount: Long,
    val rtfSize: Long,
    val imageCount: Long,
    val imageSize: Long,
    val fileCount: Long,
    val fileSize: Long,
)
