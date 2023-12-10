package com.clipevery.config

import com.clipevery.app.AppFileType
import com.clipevery.presist.FilePersist
import kotlinx.coroutines.CoroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DefaultConfigManager(ioScope: CoroutineScope) : ConfigManager(ioScope), KoinComponent {

    private val filePersist: FilePersist by inject()

    private val configFilePersist = filePersist.getPersist("appConfig.json", AppFileType.USER)

    override fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)
    }

    override fun saveConfigImpl(config: AppConfig) {
        configFilePersist.save(config)
    }
}