package com.crosspaste.paste.item

import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

@Serializable
@SerialName("text")
class TextPasteItem : RealmObject, PasteItem, PasteText {

    companion object {}

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    override var text: String = ""

    @Index
    override var favorite: Boolean = false

    override var size: Long = 0L

    override var hash: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

    override var extraInfo: String? = null

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getPasteType(): Int {
        return PasteType.TEXT
    }

    override fun getSearchContent(): String {
        return text.lowercase()
    }

    override fun update(
        data: Any,
        hash: String,
    ) {
        (data as? String)?.let { text ->
            this.text = text
            this.hash = hash
        }
    }

    override fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean,
    ) {
        realm.delete(this)
    }
}
