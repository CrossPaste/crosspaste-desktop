package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import java.util.concurrent.atomic.AtomicLong

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
