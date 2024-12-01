package com.crosspaste.paste.item

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteType
import io.realm.kotlin.MutableRealm
import kotlinx.serialization.Serializable
import org.mongodb.kbson.ObjectId

@Serializable
sealed interface PasteItem {

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
