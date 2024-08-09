package com.crosspaste.dao.paste

import com.crosspaste.path.UserDataPathProvider
import io.realm.kotlin.MutableRealm
import org.mongodb.kbson.ObjectId

interface PasteItem {

    var id: ObjectId

    fun getIdentifierList(): List<String>

    fun getPasteType(): Int

    fun getSearchContent(): String?

    var favorite: Boolean

    var md5: String

    var size: Long

    var pasteState: Int

    var extraInfo: String?

    fun update(
        data: Any,
        md5: String,
    )

    fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    )
}
