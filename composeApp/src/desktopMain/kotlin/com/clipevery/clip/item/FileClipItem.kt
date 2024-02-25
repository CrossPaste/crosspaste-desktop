package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import io.ktor.util.extension
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.nio.file.Path

class FileClipItem: RealmObject, ClipAppearItem, ClipFile {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()
    var identifier: String = ""
    var relativePath: String = ""
    override var isFile: Boolean = false
    override var md5: String = ""

    constructor()

    constructor(identifier: String, relativePath: String, isFile: Boolean, md5: String) {
        this.identifier = identifier
        this.relativePath = relativePath
        this.isFile = isFile
        this.md5 = md5
    }

    override fun getFilePath(): Path {
        return DesktopPathProvider.resolve(
            DesktopPathProvider.resolve(appFileType = AppFileType.FILE),
            relativePath, autoCreate = false)
    }

    override fun getIdentifiers(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.FILE
    }

    override fun getExtension(): String {
        return getFilePath().extension
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
        if (this.relativePath != "") {
            DesktopOneFilePersist(getFilePath()).delete()
        }
    }
}
