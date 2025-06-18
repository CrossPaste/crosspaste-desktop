package com.crosspaste.app

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.utils.getFileUtils

class DesktopAppControl(private val configManager: CommonConfigManager) : AppControl {

    private val fileUtils = getFileUtils()

    override fun isFavoriteEnabled(): Boolean {
        return true
    }

    override fun isEncryptionEnabled(): Boolean {
        return true
    }

    override fun isDeviceConnectionEnabled(deviceCount: Int): Boolean {
        return true
    }

    override fun isSyncControlEnabled(notify: Boolean): Boolean {
        return true
    }

    override suspend fun isSendEnabled(): Boolean {
        return true
    }

    override suspend fun isReceiveEnabled(): Boolean {
        return true
    }

    override suspend fun completeSendOperation() {
        // do nothing
    }

    override suspend fun completeReceiveOperation() {
        // do nothing
    }

    override fun isFileSizeSyncEnabled(size: Long): Boolean {
        return !configManager.getCurrentConfig().enabledSyncFileSizeLimit ||
            fileUtils.bytesSize(configManager.getCurrentConfig().maxSyncFileSize) > size
    }
}
