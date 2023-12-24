package com.clipevery.config

import com.clipevery.app.AppFileType
import com.clipevery.presist.FilePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class DefaultConfigManager(filePersist: FilePersist) : ConfigManager() {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val configFilePersist = filePersist.getPersist("appConfig.json", AppFileType.USER)

    override fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)
    }

    override fun ioScope(): CoroutineScope {
        return scope
    }

    override fun saveConfigImpl(config: AppConfig) {
        configFilePersist.save(config)
    }
}