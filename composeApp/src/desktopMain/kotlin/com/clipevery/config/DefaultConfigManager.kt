package com.clipevery.config

import com.clipevery.app.AppEnv
import com.clipevery.presist.OneFilePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class DefaultConfigManager(private val configFilePersist: OneFilePersist,
                           appEnv: AppEnv) : ConfigManager(configFilePersist, appEnv) {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun ioScope(): CoroutineScope {
        return scope
    }

    override fun saveConfigImpl(config: AppConfig) {
        configFilePersist.save(config)
    }
}