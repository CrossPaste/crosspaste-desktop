package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.dao.paste.PasteItem
import com.crosspaste.dao.paste.PasteState
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.serializer.PathStringRealmListSerializer
import com.crosspaste.serializer.StringRealmListSerializer
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.realmListOf
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

    companion object {
        val fileUtils = getFileUtils()
        val jsonUtils = getJsonUtils()
    }

    @PrimaryKey
    @Transient
    override var id: ObjectId = BsonObjectId()

    @Serializable(with = StringRealmListSerializer::class)
    var identifiers: RealmList<String> = realmListOf()

    @Serializable(with = PathStringRealmListSerializer::class)
    override var relativePathList: RealmList<String> = realmListOf()

    var fileInfoTree: String = ""

    @Index
    override var favorite: Boolean = false

    override var count: Long = 0L

    @Transient
    override var basePath: String? = null

    override var size: Long = 0L

    override var hash: String = ""

    @Index
    @Transient
    override var pasteState: Int = PasteState.LOADING

    override var extraInfo: String? = null

    override fun getAppFileType(): AppFileType {
        return AppFileType.FILE
    }

    override fun getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path> {
        val basePath = basePath?.toPath() ?: userDataPathProvider.resolve(appFileType = getAppFileType())
        return relativePathList.map { relativePath ->
            userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
        }
    }

    override fun getFileInfoTreeMap(): Map<String, FileInfoTree> {
        return jsonUtils.JSON.decodeFromString(fileInfoTree)
    }

    override fun getPasteFiles(userDataPathProvider: UserDataPathProvider): List<PasteFile> {
        val fileInfoTreeMap = getFileInfoTreeMap()
        return getFilePaths(userDataPathProvider).flatMap { path ->
            val fileInfoTree = fileInfoTreeMap[path.name]!!
            fileInfoTree.getPasteFileList(path)
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
        hash: String,
    ) {}

    override fun clear(
        realm: MutableRealm,
        userDataPathProvider: UserDataPathProvider,
        clearResource: Boolean,
    ) {
        if (clearResource) {
            // Non-reference types need to clean up copied files
            if (basePath == null) {
                for (path in getFilePaths(userDataPathProvider)) {
                    fileUtils.deleteFile(path)
                }
            }
        }
        realm.delete(this)
    }
}
