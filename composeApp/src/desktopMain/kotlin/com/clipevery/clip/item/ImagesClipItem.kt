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
class ImagesClipItem: RealmObject, ClipAppearItem, ClipFiles {

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    var identifierList: RealmList<String> = realmListOf()

    var relativePathList: RealmList<String> = realmListOf()

    var md5List: RealmList<String> = realmListOf()

    override var md5: String = ""

    override fun getFilePaths(): List<Path> {
        val basePath = DesktopPathProvider.resolve(appFileType = AppFileType.IMAGE)
        return relativePathList.map { relativePath ->
            DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getFileMd5List(): List<String> {
        return md5List
    }

    override fun getClipFiles(): List<ClipFile> {
        return getFilePaths().mapIndexed { index, path -> object: ClipFile {
                override fun getFilePath(): Path {
                    return path
                }

                override fun getMd5(): String {
                    return md5List[index]
                }
            }
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
