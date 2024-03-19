package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.presist.FileInfoTree
import com.clipevery.utils.JsonUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
@SerialName("files")
class FilesClipItem: RealmObject, ClipAppearItem, ClipFiles {

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifierList: RealmList<String> = realmListOf()

    var relativePathList: RealmList<String> = realmListOf()

    var fileInfoTree: String = ""

    override var md5: String = ""

    override fun getFilePaths(): List<Path> {
        val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.FILE)
        return relativePathList.map { relativePath ->
            DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getFileInfoTreeMap(): Map<String, FileInfoTree> {
        return JsonUtils.JSON.decodeFromString(fileInfoTree)
    }

    override fun getClipFiles(): List<ClipFile> {
        val fileInfoTreeMap = getFileInfoTreeMap()
        return getFilePaths().flatMap { path ->
            val fileInfoTree = fileInfoTreeMap[path.fileName.toString()]!!
            fileInfoTree.getClipFileList(path)
        }
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

    override fun clear(realm: MutableRealm, clearResource: Boolean) {
        if (clearResource) {
            for (path in getFilePaths()) {
                DesktopOneFilePersist(path).delete()
            }
        }
        realm.delete(this)
    }

    override fun fillDataFlavor(map: MutableMap<DataFlavor, Any>) {
        val fileList: List<File> = getFilePaths().map { it.toFile() }
        map[DataFlavor.javaFileListFlavor] = fileList
    }
}
