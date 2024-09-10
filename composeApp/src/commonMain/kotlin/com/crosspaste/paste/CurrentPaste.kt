package com.crosspaste.paste

import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import io.github.oshai.kotlinlogging.KotlinLogging
import org.mongodb.kbson.ObjectId

abstract class CurrentPaste {

    protected val logger = KotlinLogging.logger {}

    abstract val pasteRealm: PasteRealm

    abstract suspend fun setPasteId(
        id: ObjectId,
        updateCreateTime: Boolean = false,
    )

    abstract fun getPasteId(): ObjectId?

    fun getCurrentPaste(): PasteData? {
        return getPasteId()?.let { id ->
            logger.info { "Getting current paste with id $id" }
            return pasteRealm.getPasteData(id)?.let { pasteData ->
                return pasteData
            }
        }
    }
}
