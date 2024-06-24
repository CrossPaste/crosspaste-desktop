package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipItem
import com.clipevery.dao.clip.ClipState
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.DataFlavor

@Serializable
@SerialName("text")
class TextClipItem : RealmObject, ClipItem, ClipText {

    companion object {}

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    override var text: String = ""

    @Index
    override var favorite: Boolean = false

    override var size: Long = 0L

    override var md5: String = ""

    @Index
    @Transient
    override var clipState: Int = ClipState.LOADING

    override var extraInfo: String? = null

    override fun getIdentifierList(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.TEXT
    }

    override fun getSearchContent(): String {
        return text.lowercase()
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
