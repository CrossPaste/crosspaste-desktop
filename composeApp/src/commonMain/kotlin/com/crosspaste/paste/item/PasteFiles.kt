package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.presist.FileInfoTree
import okio.Path

interface PasteFiles {

    var count: Long

    var basePath: String?

    fun getAppFileType(): AppFileType

    fun getRelativePaths(): List<String>

    fun getFilePaths(): List<Path>

    fun getFileInfoTreeMap(): Map<String, FileInfoTree>

    fun getPasteFiles(): List<PasteFile>
}
