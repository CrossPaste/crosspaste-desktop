package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteData
import io.github.oshai.kotlinlogging.KotlinLogging
import org.mongodb.kbson.ObjectId

abstract class CurrentPaste {

    protected val logger = KotlinLogging.logger {}

    abstract val pasteDao: PasteDao

    abstract fun setPasteId(id: ObjectId)

    abstract fun getPasteId(): ObjectId?

    fun getCurrentPaste(): PasteData? {
        return getPasteId()?.let { id ->
            logger.info { "Getting current paste with id $id" }
            return pasteDao.getPasteData(id)?.let { pasteData ->
                return pasteData
            }
        }
    }
}
