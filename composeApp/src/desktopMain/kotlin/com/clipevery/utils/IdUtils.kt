package com.clipevery.utils

import com.clipevery.dao.clip.ClipDao
import java.util.concurrent.atomic.AtomicLong

class IDGeneratorFactory(private val clipDao: ClipDao) {

    fun createIDGenerator(): IDGenerator {
        val initId = AtomicLong(clipDao.getMaxClipId())
        return IDGenerator(initId)
    }
}

class IDGenerator(private val initId: AtomicLong) {
    fun nextID(): Long {
        return initId.addAndGet(1)
    }
}