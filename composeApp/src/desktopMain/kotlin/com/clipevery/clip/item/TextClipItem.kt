package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.types.RealmObject

class TextClipItem: RealmObject, ClipAppearItem, ClipText {

    var identifier: String = ""

    override var text: String = ""

    override var md5: String = ""

    override fun getIdentifiers(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.TEXT
    }

    override fun getSearchContent(): String {
        return text
    }

    override fun update(data: Any, md5: String) {
        (data as? String)?.let { text ->
            this.text = text
            this.md5 = md5
        }
    }

    override fun clear() {
        // do nothing
    }
}
