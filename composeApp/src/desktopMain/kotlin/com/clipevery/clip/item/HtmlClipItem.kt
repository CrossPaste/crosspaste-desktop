package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

class HtmlClipItem: RealmObject, ClipAppearItem, ClipHtml {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()
    var identifier: String = ""
    override var html: String = ""
    override var md5: String = ""

    override fun getIdentifiers(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.HTML
    }

    override fun getSearchContent(): String? {
        return html
    }

    override fun update(data: Any, md5: String) {
        (data as? String)?.let { html ->
            this.html = html
            this.md5 = md5
        }
    }

    override fun clear() {
        // do nothing
    }
}
