package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.presist.FileInfoTree
import com.clipevery.serializer.StringRealmListSerializer
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
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

@Serializable
@SerialName("images")
class ImagesClipItem: RealmObject, ClipAppearItem, ClipFiles {

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    @Serializable(with = StringRealmListSerializer::class)
    var identifierList: RealmList<String> = realmListOf()

    @Serializable(with = StringRealmListSerializer::class)
    var relativePathList: RealmList<String> = realmListOf()

    var fileInfoTree: String = ""

    override var md5: String = ""

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
        return JsonUtils.JSON.decodeFromString(fileInfoTree)
    }

    override fun getClipFiles(): List<ClipFile> {
        val fileTreeMap = getFileInfoTreeMap()
        return getFilePaths().flatMap { path ->
            val fileTree = fileTreeMap[path.fileName.toString()]!!
            fileTree.getClipFileList(path)
        }
    }

    override fun getIdentifiers(): List<String> {
        return identifierList
    }

    override fun getClipType(): Int {
        return ClipType.IMAGE
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
        val filePaths = getFilePaths()
        if (filePaths.size == 1) {
            val imageFile = filePaths[0].toFile()
            val bufferedImage: Image = ImageIO.read(imageFile)
            map[DataFlavor.imageFlavor] = bufferedImage
            map[DataFlavor.javaFileListFlavor] = listOf(imageFile)
        } else {
            val fileList: List<File> = filePaths.map { it.toFile() }
            map[DataFlavor.javaFileListFlavor] = fileList
        }
    }
}
