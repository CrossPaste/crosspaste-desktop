package com.clipevery.config

import com.clipevery.app.AppEnv
import com.clipevery.presist.OneFilePersist
import com.clipevery.utils.DeviceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DefaultConfigManager(
    private val configFilePersist: OneFilePersist,
    deviceUtils: DeviceUtils,
    appEnv: AppEnv,
) : ConfigManager(configFilePersist, deviceUtils, appEnv) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun ioScope(): CoroutineScope {
        return scope
    }

    override fun saveConfigImpl(config: AppConfig) {
        configFilePersist.save(config)
    }
}
