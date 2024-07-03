package com.crosspaste.presist

import com.crosspaste.app.AppFileType
import com.crosspaste.path.PathProvider
import java.nio.file.Path

interface FilePersist {

    val pathProvider: PathProvider

    fun getPersist(
        configName: String,
        appFileType: AppFileType,
    ): OneFilePersist {
        return createOneFilePersist(pathProvider.resolve(configName, appFileType))
    }

    fun createOneFilePersist(path: Path): OneFilePersist
}
