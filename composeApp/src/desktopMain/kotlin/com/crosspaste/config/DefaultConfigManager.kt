package com.crosspaste.config

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.ui.base.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DefaultConfigManager(
    private val configFilePersist: OneFilePersist,
    toastManager: ToastManager,
    lazyCopywriter: Lazy<GlobalCopywriter>,
) : ConfigManager(configFilePersist, toastManager, lazyCopywriter) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun ioScope(): CoroutineScope {
        return scope
    }

    override fun saveConfigImpl(config: AppConfig) {
        configFilePersist.save(config)
    }
}
