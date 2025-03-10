package com.crosspaste.app

import com.crosspaste.config.ConfigManager
import com.crosspaste.utils.getFileUtils

class DesktopAppControl(private val configManager: ConfigManager) : AppControl {

    private val fileUtils = getFileUtils()

    override fun isFavoriteEnabled(): Boolean {
        return true
    }

    override fun isEncryptionEnabled(): Boolean {
        return true
    }

    override fun isDeviceConnectionEnabled(num: Int): Boolean {
        return true
    }

    override fun isDeviceControlEnabled(): Boolean {
        return true
    }

    override fun isSendEnabled(): Boolean {
        return true
    }

    override fun isReceiveEnabled(): Boolean {
        return true
    }

    override fun completeSendOperation() {
        // do nothing
    }

    override fun completeReceiveOperation() {
        // do nothing
    }

    override fun isFileSizeSyncEnabled(size: Long): Boolean {
        return !configManager.config.enabledSyncFileSizeLimit ||
            fileUtils.bytesSize(configManager.config.maxSyncFileSize) > size
    }
}
