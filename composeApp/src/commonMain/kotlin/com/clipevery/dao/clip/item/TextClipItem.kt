package com.clipevery.dao.clip.item

import com.clipevery.clip.item.ClipText
import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.types.RealmObject

class TextClipItem: RealmObject, ClipAppearItem, ClipText {
    override fun getText(): String {
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
