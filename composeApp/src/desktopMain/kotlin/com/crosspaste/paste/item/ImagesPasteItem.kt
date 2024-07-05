package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.paste.LinuxPasteboardService.Companion.GNOME_COPIED_FILES_FLAVOR
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.DesktopOneFilePersist
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.serializer.PathStringRealmListSerializer
import com.crosspaste.serializer.StringRealmListSerializer
import com.crosspaste.utils.DesktopJsonUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import java.awt.datatransfer.DataFlavor
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

@Serializable
@SerialName("images")
class ImagesPasteItem : RealmObject, PasteItem, PasteImages {

    companion object {
        private val logger: KLogger = KotlinLogging.logger {}
    }

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    @Serializable(with = StringRealmListSerializer::class)
    var identifiers: RealmList<String> = realmListOf()

    @Serializable(with = PathStringRealmListSerializer::class)
    var relativePathList: RealmList<String> = realmListOf()

    var fileInfoTree: String = ""

    @Index
    override var favorite: Boolean = false

    override var count: Long = 0L

    override var size: Long = 0L

    override var md5: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

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

    override fun getPasteFiles(): List<PasteFile> {
        val fileTreeMap = getFileInfoTreeMap()
        return getFilePaths().flatMap { path ->
            val fileTree = fileTreeMap[path.fileName.toString()]!!
            fileTree.getPasteFileList(path)
        }
    }

    override fun getIdentifierList(): List<String> {
        return identifiers
    }

    override fun getPasteType(): Int {
        return PasteType.IMAGE
    }

    override fun getSearchContent(): String {
        return relativePathList.joinToString(separator = " ") { path ->
            Paths.get(path).fileName.toString().lowercase()
        }
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

        if (fileList.size == 1) {
            try {
                map[DataFlavor.imageFlavor] = ImageIO.read(fileList[0])
            } catch (e: Exception) {
                logger.error(e) { "read image fail" }
            }
        }

        if (currentPlatform().isLinux()) {
            val content =
                fileList.joinToString(
                    separator = "\n",
                    prefix = "copy\n",
                ) { it.toURI().toString() }
            val inputStream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
            map[GNOME_COPIED_FILES_FLAVOR] = inputStream
        }
    }
}