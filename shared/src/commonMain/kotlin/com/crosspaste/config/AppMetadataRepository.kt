package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DeviceUtils

class AppMetadataRepository(
    private val persist: OneFilePersist,
    private val deviceUtils: DeviceUtils,
) {
    val appInstanceId: String by lazy { loadOrCreate().appInstanceId }

    private fun loadOrCreate(): AppMetadata =
        persist.read(AppMetadata::class)
            ?: AppMetadata(deviceUtils.createAppInstanceId()).also { persist.save(it) }
}
