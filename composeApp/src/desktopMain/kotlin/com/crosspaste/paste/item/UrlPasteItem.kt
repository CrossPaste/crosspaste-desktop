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
@SerialName("url")
class UrlPasteItem : RealmObject, PasteItem, PasteUrl {

    companion object {}

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    override var url: String = ""

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
        return PasteType.URL
    }

    override fun getSearchContent(): String {
        return url.lowercase()
    }

    override fun update(
        data: Any,
        hash: String,
    ) {
        (data as? String)?.let { url ->
            this.url = url
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
