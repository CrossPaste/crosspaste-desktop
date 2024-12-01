package com.crosspaste.paste.item

import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
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
@SerialName("color")
class ColorPasteItem : RealmObject, PasteItem, PasteColor {

    companion object {}

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    override var color: Long = 0L

    @Index
    override var favorite: Boolean = false

    override var size: Long = 4L

    override var hash: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

    override var extraInfo: String? = null

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getPasteType(): PasteType {
        return PasteType.COLOR_TYPE
    }

    override fun getSearchContent(): String {
        return toHexString()
    }

    override fun getTitle(): String {
        return toHexString()
    }

    override fun update(
        data: Any,
        hash: String,
    ) {
        (data as? Long)?.let { color ->
            this.color = color
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
