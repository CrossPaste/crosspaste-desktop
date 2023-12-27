package com.clipevery.config

import com.clipevery.presist.OneFilePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class DefaultConfigManager(private val configFilePersist: OneFilePersist) : ConfigManager() {

    private val scope = CoroutineScope(Dispatchers.IO)

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