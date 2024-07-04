package com.crosspaste.utils

import com.crosspaste.dao.paste.PasteDao
import java.util.concurrent.atomic.AtomicLong

class IDGeneratorFactory(private val pasteDao: PasteDao) {

    fun createIDGenerator(): IDGenerator {
        val initId = AtomicLong(pasteDao.getMaxPasteId())
        return IDGenerator(initId)
    }
}

class IDGenerator(private val initId: AtomicLong) {
    fun nextID(): Long {
        return initId.addAndGet(1)
    }
}
