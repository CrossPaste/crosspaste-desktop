package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.utils.getFileUtils
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.types.RealmList
import okio.Path
import okio.Path.Companion.toPath

interface PasteFiles {

    var relativePathList: RealmList<String>

    var count: Long

    var basePath: String?

    fun getAppFileType(): AppFileType

    fun getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path>

    fun getFileInfoTreeMap(): Map<String, FileInfoTree>

    fun getPasteFiles(userDataPathProvider: UserDataPathProvider): List<PasteFile>

    // use to adapt relative paths when relative is no storage in crossPaste
    fun adaptRelativePaths(
        appInstanceId: String,
        pasteId: Long,
    ) {
        val noStorageInCrossPaste = relativePathList.any { it.toPath().segments.size == 1 }
        if (noStorageInCrossPaste) {
            val fileUtils = getFileUtils()
            relativePathList =
                relativePathList.map { relativePath ->
                    val path = relativePath.toPath()
                    val fileName = path.name
                    fileUtils.createPasteRelativePath(
                        appInstanceId = appInstanceId,
                        pasteId = pasteId,
                        fileName = fileName,
                    )
                }.toRealmList()
        }
    }
}
