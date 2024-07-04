package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.presist.FileInfoTree
import java.nio.file.Path

interface PasteFiles {

    var count: Long

    fun getAppFileType(): AppFileType

    fun getRelativePaths(): List<String>

    fun getFilePaths(): List<Path>

    fun getFileInfoTreeMap(): Map<String, FileInfoTree>

    fun getPasteFiles(): List<PasteFile>
}
