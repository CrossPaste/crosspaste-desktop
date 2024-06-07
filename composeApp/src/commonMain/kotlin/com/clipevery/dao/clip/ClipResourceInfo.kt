package com.clipevery.dao.clip

data class ClipResourceInfo(
    val clipCount: Long,
    val clipSize: Long,
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
