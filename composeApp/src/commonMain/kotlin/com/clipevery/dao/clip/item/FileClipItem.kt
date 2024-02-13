package com.clipevery.dao.clip.item

import com.clipevery.clip.item.ClipFile
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import io.realm.kotlin.types.RealmObject
import java.io.File

class FileClipItem: RealmObject, ClipAppearItem, ClipFile {

    var identifier: String = ""
    var relativePath: String = ""
    var isFile: Boolean = false
    var md5: String = ""

    constructor()

    constructor(file: File, extension: String, identifier: String, relativePath: String, isFile: Boolean, md5: String) {
        this.identifier = identifier
        this.relativePath = relativePath
        this.isFile = isFile
        this.md5 = md5
    }

    override fun getIdentifiers(): List<String> {
        return listOf(identifier)
    }

    override fun getClipType(): Int {
        return ClipType.FILE
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

        }
    }

    override fun getFile(): File {
        TODO("Not yet implemented")
    }

    override fun getExtension(): String {
        TODO("Not yet implemented")
    }

}
