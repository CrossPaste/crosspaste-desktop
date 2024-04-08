package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.DataFlavor

@Serializable
@SerialName("text")
class TextClipItem : RealmObject, ClipAppearItem, ClipText {

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()
    var identifier: String = ""
    override var text: String = ""
    override var md5: String = ""

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.TEXT
    }

    override fun getSearchContent(): String {
        return text
    }

    override fun update(
        data: Any,
        md5: String,
    ) {
        (data as? String)?.let { text ->
            this.text = text
            this.md5 = md5
        }
    }

    override fun clear(
        realm: MutableRealm,
        clearResource: Boolean,
    ) {
        realm.delete(this)
    }

    override fun fillDataFlavor(map: MutableMap<DataFlavor, Any>) {
        map[DataFlavor.stringFlavor] = text
    }
}
