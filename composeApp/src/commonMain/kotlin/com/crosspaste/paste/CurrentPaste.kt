package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteData
import org.mongodb.kbson.ObjectId

interface CurrentPaste {

    fun setPasteId(id: ObjectId)

    fun getCurrentPaste(): PasteData?
}
