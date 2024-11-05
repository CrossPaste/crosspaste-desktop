package com.crosspaste.realm.paste

import com.crosspaste.path.UserDataPathProvider
import io.realm.kotlin.MutableRealm
import org.mongodb.kbson.ObjectId

interface PasteItem {

    var id: ObjectId

    var favorite: Boolean

    var hash: String

    var size: Long

    var pasteState: Int

    var extraInfo: String?

    fun getIdentifierList(): List<String>

    fun getPasteType(): PasteType

    fun getSearchContent(): String?

    fun getTitle(): String

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
