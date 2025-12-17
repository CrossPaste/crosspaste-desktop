package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import java.util.concurrent.atomic.AtomicReference

class DesktopCurrentPaste(
    private val lazyPasteDao: Lazy<PasteDao>,
) : CurrentPaste() {

    private val currentId: AtomicReference<Long?> = AtomicReference<Long?>()

    override val pasteDao: PasteDao by lazy { lazyPasteDao.value }

    override suspend fun setPasteId(
        id: Long,
        updateCreateTime: Boolean,
    ) {
        logger.info { "Setting current paste id to $id" }
        currentId.set(id)
        if (updateCreateTime) {
            pasteDao.updateCreateTime(id)
        }
    }

    override fun getPasteId(): Long? = currentId.get()
}
