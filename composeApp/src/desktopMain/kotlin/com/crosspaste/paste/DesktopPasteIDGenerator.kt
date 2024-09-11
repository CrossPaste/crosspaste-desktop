package com.crosspaste.paste

import com.crosspaste.realm.paste.PasteRealm
import java.util.concurrent.atomic.AtomicLong

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
