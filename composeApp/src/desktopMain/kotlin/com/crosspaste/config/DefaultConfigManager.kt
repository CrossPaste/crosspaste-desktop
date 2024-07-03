package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DefaultConfigManager(
    private val configFilePersist: OneFilePersist,
) : ConfigManager(configFilePersist) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun ioScope(): CoroutineScope {
        return scope
    }

    override fun saveConfigImpl(config: AppConfig) {
        configFilePersist.save(config)
    }
}
