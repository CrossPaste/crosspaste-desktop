package com.clipevery.utils

import com.clipevery.dao.clip.ClipDao
import java.util.concurrent.atomic.AtomicInteger

class IDGeneratorFactory(private val clipDao: ClipDao) {

    fun createIDGenerator(): IDGenerator {
        val initId = AtomicInteger(clipDao.getMaxClipId())
        return IDGenerator(initId)
    }
}

class IDGenerator(private val initId: AtomicInteger) {
    fun nextID(): Int {
        return initId.addAndGet(1)
    }
}