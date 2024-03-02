package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

class UrlClipItem: RealmObject, ClipAppearItem, ClipUrl {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()
    var identifier: String = ""
    override var url: String = ""
    override var md5: String = ""

    override fun getIdentifiers(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.URL
    }

    override fun getSearchContent(): String {
        return url
    }

    override fun update(data: Any, md5: String) {
        (data as? String)?.let { url ->
            this.url = url
            this.md5 = md5
        }
    }

    override fun clear(realm: MutableRealm) {
        // do nothing
        realm.delete(this)
    }
}
