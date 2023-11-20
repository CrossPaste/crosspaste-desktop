package com.clipevery.path

import com.clipevery.config.ConfigType
import java.nio.file.Path

interface PathProvider {
    fun resolve(configName: String, configType: ConfigType): Path {
        return when (configType) {
            ConfigType.USER -> resolveUser(configName)
            ConfigType.SYSTEM -> resolveApp(configName)
        }
    }

    fun resolveUser(configName: String): Path

    fun resolveApp(configName: String): Path
}