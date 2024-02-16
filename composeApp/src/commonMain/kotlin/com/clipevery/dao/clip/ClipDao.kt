package com.clipevery.dao.clip

interface ClipDao {

    fun getMaxClipId(): Int

    fun createClipData(clipData: ClipData)
}