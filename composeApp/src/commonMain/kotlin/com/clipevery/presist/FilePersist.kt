package com.clipevery.presist

import com.clipevery.config.ConfigType
import com.clipevery.path.PathProvider
import java.nio.file.Path

interface FilePersist {

    val pathProvider: PathProvider

    fun getPersist(configName: String, configType: ConfigType): OneFilePersist {
        return createOneFilePersist(pathProvider.resolve(configName, configType))
    }

    fun createOneFilePersist(path: Path): OneFilePersist
}