package com.crosspaste.paste

import co.touchlab.stately.concurrency.AtomicLong
import com.crosspaste.realm.paste.PasteRealm

class DesktopPasteIDGeneratorFactory(private val pasteRealm: PasteRealm) :
    PasteIDGeneratorFactory() {

    override fun createIDGenerator(): PasteIDGenerator {
        return DesktopPasteIDGenerator(pasteRealm.getMaxPasteId())
    }
}

class DesktopPasteIDGenerator(initId: Long) :
    PasteIDGenerator() {

    private val id = AtomicLong(initId)

    override fun nextID(): Long {
        return id.incrementAndGet()
    }
}
