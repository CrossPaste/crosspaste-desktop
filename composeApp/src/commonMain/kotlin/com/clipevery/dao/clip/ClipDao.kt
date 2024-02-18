package com.clipevery.dao.clip

import org.mongodb.kbson.ObjectId

interface ClipDao {

    fun getMaxClipId(): Int

    fun createClipData(clipData: ClipData)

    fun deleteClipData(id: ObjectId)
}