package com.clipevery.dao.clip

import io.realm.kotlin.MutableRealm
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.DataFlavor

interface ClipItem {

    var id: ObjectId

    fun getIdentifierList(): List<String>

    fun getClipType(): Int

    fun getSearchContent(): String?

    var favorite: Boolean

    var md5: String

    var size: Long

    var clipState: Int

    var extraInfo: String?

    fun update(
        data: Any,
        md5: String,
    )

    fun clear(
        realm: MutableRealm,
        clearResource: Boolean = true,
    )

    fun fillDataFlavor(map: MutableMap<DataFlavor, Any>)
}