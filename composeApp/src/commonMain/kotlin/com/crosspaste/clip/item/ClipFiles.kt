package com.crosspaste.clip.item

import com.crosspaste.app.AppFileType
import com.crosspaste.presist.FileInfoTree
import java.nio.file.Path

interface ClipFiles {

    var count: Long

    fun getAppFileType(): AppFileType

    fun getRelativePaths(): List<String>

    fun getFilePaths(): List<Path>

    fun getFileInfoTreeMap(): Map<String, FileInfoTree>

    fun getClipFiles(): List<ClipFile>
}
