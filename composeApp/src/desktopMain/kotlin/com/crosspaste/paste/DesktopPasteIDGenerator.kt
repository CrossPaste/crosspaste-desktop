package com.crosspaste.paste

import co.touchlab.stately.concurrency.AtomicLong
import com.crosspaste.dao.paste.PasteDao

class DesktopPasteIDGeneratorFactory(private val pasteDao: PasteDao) :
    PasteIDGeneratorFactory() {

    override fun createIDGenerator(): PasteIDGenerator {
        return DesktopPasteIDGenerator(pasteDao.getMaxPasteId())
    }
}

class DesktopPasteIDGenerator(initId: Long) :
    PasteIDGenerator() {

    private val id = AtomicLong(initId)

    override fun nextID(): Long {
        return id.incrementAndGet()
    }
}
