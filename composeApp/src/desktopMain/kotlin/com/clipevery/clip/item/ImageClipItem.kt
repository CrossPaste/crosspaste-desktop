package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.types.RealmObject
import java.awt.Image

class ImageClipItem: RealmObject, ClipAppearItem, ClipImage {
    override fun getImage(): Image {
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

    override fun getMD5(): String {
        TODO("Not yet implemented")
    }

    override fun update(data: Any, md5: String) {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }
}
