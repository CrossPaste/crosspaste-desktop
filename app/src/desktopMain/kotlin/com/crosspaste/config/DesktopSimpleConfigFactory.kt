package com.crosspaste.config

import com.crosspaste.app.AppFileType
import com.crosspaste.path.AppPathProvider
import com.crosspaste.presist.FilePersist

class DesktopSimpleConfigFactory(
    private val appPathProvider: AppPathProvider,
) : SimpleConfigFactory {

    override fun createConfig(configName: String): SimpleConfig {
        val oneFilePersist =
            FilePersist.createOneFilePersist(
                appPathProvider.resolve(configName, AppFileType.USER),
            )
        return DesktopSimpleConfig(oneFilePersist)
    }
}
