package com.crosspaste.app

interface AppControl {

    fun isFavoriteEnabled(): Boolean

    fun isEncryptionEnabled(): Boolean

    fun isDeviceConnectionEnabled(deviceCount: Int): Boolean

    fun isDeviceControlEnabled(): Boolean

    fun isSendEnabled(): Boolean

    fun isReceiveEnabled(): Boolean

    fun completeSendOperation()

    fun completeReceiveOperation()

    fun isFileSizeSyncEnabled(size: Long): Boolean
}
