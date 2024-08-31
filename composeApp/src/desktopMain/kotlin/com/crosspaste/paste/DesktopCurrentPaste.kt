package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import org.mongodb.kbson.ObjectId
import java.util.concurrent.atomic.AtomicReference

class DesktopCurrentPaste(private val lazyPasteDao: Lazy<PasteDao>) : CurrentPaste {

    private val currentId: AtomicReference<ObjectId?> = AtomicReference<ObjectId?>()

    private val pasteDao: PasteDao by lazy { lazyPasteDao.value }

    override fun setPasteId(id: ObjectId) {
        println("set id = $id")
        currentId.set(id)
    }

    override fun getCurrentPaste(): PasteData? {
        return currentId.get()?.let { id ->
            println("get id = $id")
            return pasteDao.getPasteData(id)?.let { pasteData ->
                return pasteData
            }
        }
    }
}
