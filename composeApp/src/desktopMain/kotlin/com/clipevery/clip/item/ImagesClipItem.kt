package com.clipevery.clip.item

import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.nio.file.Path

class ImagesClipItem: RealmObject, ClipAppearItem, ClipFiles {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()
    var imageClipItems: RealmList<ImageClipItem> = realmListOf()
    override var md5: String = ""

    override fun getFilePaths(): List<Path> {
        return imageClipItems.map { it.getImagePath() }
    }

    override fun getIdentifiers(): List<String> {
        return imageClipItems.map { it.identifier }
    }

    override fun getClipType(): Int {
        return ClipType.IMAGE
    }

    override fun getSearchContent(): String {
        return imageClipItems.map { it.relativePath }.joinToString(separator = " ")
    }

    override fun update(data: Any, md5: String) {}

    override fun clear() {
        for (imageClipItem in imageClipItems) {
            imageClipItem.clear()
        }
    }
}
