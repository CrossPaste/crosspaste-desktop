package com.crosspaste.app

interface AppControl {

    fun isFavoriteEnabled(): Boolean

    fun isEncryptionEnabled(): Boolean

    fun isDeviceConnectionEnabled(deviceCount: Int): Boolean

    fun isSyncControlEnabled(notify: Boolean = false): Boolean

    suspend fun isSendEnabled(): Boolean

    suspend fun isReceiveEnabled(): Boolean

    suspend fun completeSendOperation()

    suspend fun completeReceiveOperation()

    fun isFileSizeSyncEnabled(size: Long): Boolean
}
