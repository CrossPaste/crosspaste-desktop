package com.crosspaste.clip.item

import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.dao.clip.ClipState
import com.crosspaste.dao.clip.ClipType
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
import java.net.URL

@Serializable
@SerialName("url")
class UrlClipItem : RealmObject, ClipItem, ClipUrl {

    companion object {}

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifier: String = ""

    override var url: String = ""

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
        return ClipType.URL
    }

    override fun getSearchContent(): String {
        return url.lowercase()
    }

    override fun update(
        data: Any,
        md5: String,
    ) {
        (data as? String)?.let { url ->
            this.url = url
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
        map[DataFlavor("application/x-java-url; class=java.net.URL")] = URL(url)
    }
}