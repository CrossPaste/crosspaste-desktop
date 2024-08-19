package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import okio.Path

interface PathProvider {

    val fileUtils: FileUtils get() = getFileUtils()

    fun resolve(
        fileName: String? = null,
        appFileType: AppFileType,
    ): Path

    fun resolve(
        basePath: Path,
        path: String,
        autoCreate: Boolean = true,
        isFile: Boolean = false,
    ): Path {
        val newPath = basePath.resolve(path)
        if (autoCreate) {
            if (isFile) {
                newPath.parent?.let { autoCreateDir(it) }
            } else {
                autoCreateDir(newPath)
            }
        }
        return newPath
    }

    fun autoCreateDir(path: Path) {
        if (!fileUtils.createDir(path)) {
            throw PasteException(
                StandardErrorCode.CANT_CREATE_DIR.toErrorCode(),
                "Failed to create directory: $path",
            )
        }
    }
}
