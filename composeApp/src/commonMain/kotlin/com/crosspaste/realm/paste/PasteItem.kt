package com.crosspaste.realm.paste

import com.crosspaste.path.UserDataPathProvider
import io.realm.kotlin.MutableRealm
import org.mongodb.kbson.ObjectId

interface PasteItem {

    var id: ObjectId

    fun getIdentifierList(): List<String>

    fun getPasteType(): Int

    fun getSearchContent(): String?

    var favorite: Boolean

    var hash: String

    var size: Long

    var pasteState: Int

    var extraInfo: String?

    fun update(
        data: Any,
        hash: String,
    )

    fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean = true,
    )
}
