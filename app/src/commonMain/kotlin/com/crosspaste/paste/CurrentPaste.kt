package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import io.github.oshai.kotlinlogging.KotlinLogging

abstract class CurrentPaste {

    protected val logger = KotlinLogging.logger {}

    abstract val pasteDao: PasteDao

    abstract suspend fun setPasteId(
        id: Long,
        updateCreateTime: Boolean = false,
    )

    abstract fun getPasteId(): Long?

    fun getCurrentPaste(): PasteData? {
        return getPasteId()?.let { id ->
            logger.info { "Getting current paste with id $id" }
            return pasteDao.getPasteData(id)?.let { pasteData ->
                return pasteData
            }
        }
    }
}
