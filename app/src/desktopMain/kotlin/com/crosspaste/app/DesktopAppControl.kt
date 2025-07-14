package com.crosspaste.app

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.utils.getFileUtils

class DesktopAppControl(
    private val configManager: CommonConfigManager,
) : AppControl {

    private val fileUtils = getFileUtils()

    override fun isFavoriteEnabled(): Boolean = true

    override fun isEncryptionEnabled(): Boolean = true

    override fun isDeviceConnectionEnabled(deviceCount: Int): Boolean = true

    override fun isSyncControlEnabled(notify: Boolean): Boolean = true

    override suspend fun isSendEnabled(): Boolean = true

    override suspend fun isReceiveEnabled(): Boolean = true

    override suspend fun completeSendOperation() {
        // do nothing
    }

    override suspend fun completeReceiveOperation() {
        // do nothing
    }

    override fun isFileSizeSyncEnabled(size: Long): Boolean =
        !configManager.getCurrentConfig().enabledSyncFileSizeLimit ||
            fileUtils.bytesSize(configManager.getCurrentConfig().maxSyncFileSize) > size
}
