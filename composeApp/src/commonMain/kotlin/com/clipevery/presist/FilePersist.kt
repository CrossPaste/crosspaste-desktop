package com.clipevery.presist

import com.clipevery.config.FileType
import com.clipevery.path.PathProvider
import java.nio.file.Path

interface FilePersist {

    val pathProvider: PathProvider

    fun getPersist(configName: String, fileType: FileType): OneFilePersist {
        return createOneFilePersist(pathProvider.resolve(configName, fileType))
    }

    fun createOneFilePersist(path: Path): OneFilePersist
}