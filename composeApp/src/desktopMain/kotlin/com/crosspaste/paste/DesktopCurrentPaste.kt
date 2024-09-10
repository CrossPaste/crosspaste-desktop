package com.crosspaste.paste

import com.crosspaste.realm.paste.PasteRealm
import org.mongodb.kbson.ObjectId
import java.util.concurrent.atomic.AtomicReference

class DesktopCurrentPaste(private val lazyPasteRealm: Lazy<PasteRealm>) : CurrentPaste() {

    private val currentId: AtomicReference<ObjectId?> = AtomicReference<ObjectId?>()

    override val pasteRealm: PasteRealm by lazy { lazyPasteRealm.value }

    override suspend fun setPasteId(
        id: ObjectId,
        updateCreateTime: Boolean,
    ) {
        logger.info { "Setting current paste id to $id" }
        currentId.set(id)
        if (updateCreateTime) {
            pasteRealm.updateCreateTime(id)
        }
    }

    override fun getPasteId(): ObjectId? {
        return currentId.get()
    }
}
