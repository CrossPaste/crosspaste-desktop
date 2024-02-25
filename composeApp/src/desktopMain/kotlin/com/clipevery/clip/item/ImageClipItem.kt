package com.clipevery.clip.item

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.nio.file.Path

class ImageClipItem: RealmObject, ClipAppearItem, ClipImage {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()
    var identifier: String = ""
    var relativePath: String = ""
    override var md5: String = ""

    override fun getImage(): ImageBitmap {
        return loadImageBitmap(getImagePath().toFile().inputStream())
    }

    override fun getImagePath(): Path {
        val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.IMAGE)
        return DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false)
    }

    override fun getIdentifiers(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.IMAGE
    }

    override fun getSearchContent(): String? {
        return relativePath
    }

    override fun update(data: Any, md5: String) {
        (data as? String)?.let { path ->
            this.relativePath = path
            this.md5 = md5
        }
    }

    override fun clear() {
        if (relativePath != "") {
            DesktopOneFilePersist(getImagePath()).delete()
        }
    }
}
