package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.presist.DesktopOneFilePersist
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.serializer.PathStringRealmListSerializer
import com.crosspaste.serializer.StringRealmListSerializer
import com.crosspaste.utils.DesktopFileUtils.createPasteRelativePath
import com.crosspaste.utils.DesktopJsonUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.Path
import okio.Path.Companion.toPath
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

@Serializable
@SerialName("files")
class FilesPasteItem : RealmObject, PasteItem, PasteFiles {

    companion object {}

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

    @Transient
    override var basePath: String? = null

    override var size: Long = 0L

    override var md5: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

    override var extraInfo: String? = null

    override fun getAppFileType(): AppFileType {
        return AppFileType.FILE
    }

    override fun getRelativePaths(): List<String> {
        return relativePathList
    }

    override fun getFilePaths(): List<Path> {
        val basePath = basePath?.toPath() ?: DesktopPathProvider.resolve(appFileType = getAppFileType())
        return relativePathList.map { relativePath ->
            DesktopPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getFileInfoTreeMap(): Map<String, FileInfoTree> {
        return DesktopJsonUtils.JSON.decodeFromString(fileInfoTree)
    }

    override fun getPasteFiles(): List<PasteFile> {
        val fileInfoTreeMap = getFileInfoTreeMap()
        return getFilePaths().flatMap { path ->
            val fileInfoTree = fileInfoTreeMap[path.name]!!
            fileInfoTree.getPasteFileList(path)
        }
    }

    // use to adapt relative paths when relative is no storage in crossPaste
    override fun adaptRelativePaths(
        appInstanceId: String,
        pasteId: Long,
    ) {
        val noStorageInCrossPaste = relativePathList.any { it.toPath().segments.size == 1 }
        if (noStorageInCrossPaste) {
            relativePathList =
                relativePathList.map { relativePath ->
                    val path = relativePath.toPath()
                    val fileName = path.name
                    createPasteRelativePath(
                        appInstanceId = appInstanceId,
                        pasteId = pasteId,
                        fileName = fileName,
                    )
                }.toRealmList()
        }
    }

    override fun getIdentifierList(): List<String> {
        return identifiers
    }

    override fun getPasteType(): Int {
        return PasteType.FILE
    }

    override fun getSearchContent(): String {
        return relativePathList.joinToString(separator = " ") { path ->
            path.toPath().name.lowercase()
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
            // Non-reference types need to clean up copied files
            if (basePath == null) {
                for (path in getFilePaths()) {
                    DesktopOneFilePersist(path).delete()
                }
            }
        }
        realm.delete(this)
    }
}
