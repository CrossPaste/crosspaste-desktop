package com.clipevery.clip.item

import com.clipevery.app.AppFileType
import com.clipevery.presist.FileInfoTree
import java.nio.file.Path

interface ClipFiles {

    fun getAppFileType(): AppFileType

    fun getRelativePaths(): List<String>

    fun getFilePaths(): List<Path>

    fun getFileInfoTreeMap(): Map<String, FileInfoTree>

    fun getClipFiles(): List<ClipFile>
}
