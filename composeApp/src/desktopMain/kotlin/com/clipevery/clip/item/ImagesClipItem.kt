package com.clipevery.clip.item

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.types.RealmObject
import java.io.File

class ImagesClipItem: RealmObject, ClipAppearItem, ClipFiles {
    override fun getFiles(): List<File> {
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
