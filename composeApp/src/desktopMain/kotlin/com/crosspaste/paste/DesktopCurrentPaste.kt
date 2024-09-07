package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteDao
import org.mongodb.kbson.ObjectId
import java.util.concurrent.atomic.AtomicReference

class DesktopCurrentPaste(private val lazyPasteDao: Lazy<PasteDao>) : CurrentPaste() {

    private val currentId: AtomicReference<ObjectId?> = AtomicReference<ObjectId?>()

    override val pasteDao: PasteDao by lazy { lazyPasteDao.value }

    override suspend fun setPasteId(
        id: ObjectId,
        updateCreateTime: Boolean,
    ) {
        logger.info { "Setting current paste id to $id" }
        currentId.set(id)
        if (updateCreateTime) {
            pasteDao.updateCreateTime(id)
        }
    }

    override fun getPasteId(): ObjectId? {
        return currentId.get()
    }
}
