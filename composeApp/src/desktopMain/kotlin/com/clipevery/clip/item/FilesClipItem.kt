package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import io.realm.kotlin.MutableRealm
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

    var identifierList: RealmList<String> = realmListOf()

    var relativePathList: RealmList<String> = realmListOf()

    var md5List: RealmList<String> = realmListOf()

    override var md5: String = ""

    override fun getFilePaths(): List<Path> {
        val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
        return relativePathList.map { relativePath ->
            DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getFileMd5List(): List<String> {
        return md5List
    }

    override fun getIdentifiers(): List<String> {
        return identifierList
    }

    override fun getClipType(): Int {
        return ClipType.FILE
    }

    override fun getSearchContent(): String {
        return relativePathList.map { path ->
            Paths.get(path).fileName
        }.joinToString(separator = " ")
    }

    override fun update(data: Any, md5: String) {}

    override fun clear(realm: MutableRealm) {
        for (path in getFilePaths()) {
            DesktopOneFilePersist(path).delete()
        }
        realm.delete(this)
    }
}
