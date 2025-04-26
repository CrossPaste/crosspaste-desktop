package com.crosspaste.config

import com.crosspaste.app.AppFileType
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.presist.FilePersist

class DesktopSimpleConfigFactory : SimpleConfigFactory {

    private val appPathProvider = DesktopAppPathProvider

    override fun createConfig(configName: String): SimpleConfig {
        val oneFilePersist =
            FilePersist.createOneFilePersist(
                appPathProvider.resolve(configName, AppFileType.USER),
            )
        return DesktopSimpleConfig(oneFilePersist)
    }
}
