package com.clipevery.clip.item

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.nio.file.Path

class HtmlClipItem: RealmObject, ClipAppearItem, ClipHtml {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()
    var identifier: String = ""
    var relativePath: String = ""
    override var html: String = ""


    override fun getHtmlImagePath(): Path {
        val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.HTML)
        return DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)

    }

    override fun getHtmlImage(): ImageBitmap? {
        val file = getHtmlImagePath().toFile()
        return if (file.exists()) {
            loadImageBitmap(file.inputStream())
        } else {
            null
        }
    }

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

    override fun clear(realm: MutableRealm) {
        // todo clear html image
        realm.delete(this)
    }
}
