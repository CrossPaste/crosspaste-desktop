package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import okio.Path

interface PasteFiles {

    val count: Long

    val basePath: String?

    val fileInfoTreeMap: Map<String, FileInfoTree>

    val relativePathList: List<String>

    val size: Long

    fun getAppFileType(): AppFileType

    fun getFilePaths(userDataPathProvider: UserDataPathProvider): List<Path>

    fun getPasteFiles(userDataPathProvider: UserDataPathProvider): List<PasteFile>
}
