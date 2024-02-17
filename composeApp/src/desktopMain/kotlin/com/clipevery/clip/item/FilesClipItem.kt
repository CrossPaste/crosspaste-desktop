package com.clipevery.clip.item

import com.clipevery.clip.service.FileItemService
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.nio.file.Path
import java.nio.file.Paths

class FilesClipItem: RealmObject, ClipAppearItem, ClipFiles {

    @PrimaryKey
    override var id: ObjectId = BsonObjectId()

    var identifiers: RealmList<String> = realmListOf()

    var fileList: RealmList<String> = realmListOf()

    override var md5: String = ""

    override fun getFilePaths(): List<Path> {
        return fileList.map { relativePath ->
            DesktopPathProvider.resolve(FileItemService.FILE_BASE_PATH, relativePath, autoCreate = false)
        }
    }

    override fun getIdentifiers(): List<String> {
        return identifiers.toList()
    }

    override fun getClipType(): Int {
        return ClipType.FILES
    }

    override fun getSearchContent(): String? {
        return fileList.map { path ->
            Paths.get(path).fileName
        }.joinToString(separator = " ")
    }

    override fun update(data: Any, md5: String) {}

    override fun clear() {}
}
