package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.presist.FileInfoTree
import com.clipevery.serializer.PathStringRealmListSerializer
import com.clipevery.serializer.StringRealmListSerializer
import com.clipevery.utils.DesktopJsonUtils
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
@SerialName("images")
class ImagesClipItem : RealmObject, ClipAppearItem, ClipFiles {

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    @Serializable(with = StringRealmListSerializer::class)
    var identifiers: RealmList<String> = realmListOf()

    @Serializable(with = PathStringRealmListSerializer::class)
    var relativePathList: RealmList<String> = realmListOf()

    var fileInfoTree: String = ""

    override var count: Long = 0L

    override var size: Long = 0L

    override var md5: String = ""

    override var extraInfo: String? = null

    override fun getAppFileType(): AppFileType {
        return AppFileType.IMAGE
    }

    override fun getRelativePaths(): List<String> {
        return relativePathList
    }

    override fun getFilePaths(): List<Path> {
        val basePath = DesktopPathProvider.resolve(appFileType = getAppFileType())
        return relativePathList.map { relativePath ->
            DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getFileInfoTreeMap(): Map<String, FileInfoTree> {
        return DesktopJsonUtils.JSON.decodeFromString(fileInfoTree)
    }

    override fun getClipFiles(): List<ClipFile> {
        val fileTreeMap = getFileInfoTreeMap()
        return getFilePaths().flatMap { path ->
            val fileTree = fileTreeMap[path.fileName.toString()]!!
            fileTree.getClipFileList(path)
        }
    }

    override fun getIdentifierList(): List<String> {
        return identifiers
    }

    override fun getClipType(): Int {
        return ClipType.IMAGE
    }

    override fun getSearchContent(): String {
        return relativePathList.map { path ->
            Paths.get(path).fileName
        }.joinToString(separator = " ")
    }

    override fun update(
        data: Any,
        md5: String,
    ) {}

    override fun clear(
        realm: MutableRealm,
        clearResource: Boolean,
    ) {
        if (clearResource) {
            for (path in getFilePaths()) {
                DesktopOneFilePersist(path).delete()
            }
        }
        realm.delete(this)
    }

    override fun fillDataFlavor(map: MutableMap<DataFlavor, Any>) {
        val filePaths = getFilePaths()
        val fileList: List<File> = filePaths.map { it.toFile() }
        map[DataFlavor.javaFileListFlavor] = fileList
    }
}
