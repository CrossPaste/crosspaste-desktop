package com.crosspaste.html

import com.crosspaste.module.AbstractModuleLoader
import okio.Path

class ChromeModuleLoader : AbstractModuleLoader() {

    override fun installModule(path: Path): Boolean {
        return path.toFile().setExecutable(true)
    }
}
