package com.clipevery.dao.clip.item

import com.clipevery.clip.item.ClipHtml
import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.types.RealmObject

class HtmlClipItem: RealmObject, ClipAppearItem, ClipHtml {
    override fun getHtml(): String {
        TODO("Not yet implemented")
    }

    override fun getIdentifiers(): List<String> {
        TODO("Not yet implemented")
    }

    override fun getClipType(): Int {
        TODO("Not yet implemented")
    }

    override fun getSearchContent(): String? {
        TODO("Not yet implemented")
    }

    override fun getMd5(): String {
        TODO("Not yet implemented")
    }

    override fun update(data: Any, md5: String) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }
}
