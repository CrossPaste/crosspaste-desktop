package com.clipevery.clip.item

import com.clipevery.clip.service.FileItemService
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import io.ktor.util.extension
import io.realm.kotlin.types.RealmObject
import java.nio.file.Path

class FileClipItem: RealmObject, ClipAppearItem, ClipFile {

    var identifier: String = ""
    var relativePath: String = ""
    var isFile: Boolean = false
    var md5: String = ""

    constructor()

    constructor(identifier: String, relativePath: String, isFile: Boolean, md5: String) {
        this.identifier = identifier
        this.relativePath = relativePath
        this.isFile = isFile
        this.md5 = md5
    }

    override fun getFilePath(): Path {
        return DesktopPathProvider.resolve(FileItemService.FILE_BASE_PATH, relativePath, autoCreate = false)
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

    override fun getMd5(): String {
        return md5
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
