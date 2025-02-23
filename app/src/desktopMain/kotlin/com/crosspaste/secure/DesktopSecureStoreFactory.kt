package com.crosspaste.secure

import com.crosspaste.app.AppInfo
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.platform.getPlatform

class DesktopSecureStoreFactory(
    private val appInfo: AppInfo,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureIO: SecureIO,
) : SecureStoreFactory {

    private val currentPlatform = getPlatform()

    override fun createSecureStore(): SecureStore {
        return getSecureStoreFactory().createSecureStore()
    }

    private fun getSecureStoreFactory(): SecureStoreFactory {
        return if (currentPlatform.isMacos()) {
            MacosSecureStoreFactory(appInfo, secureKeyPairSerializer, secureIO)
        } else if (currentPlatform.isWindows()) {
            WindowsSecureStoreFactory(secureKeyPairSerializer, secureIO)
        } else if (currentPlatform.isLinux()) {
            LinuxSecureStoreFactory(secureKeyPairSerializer, secureIO)
        } else {
            throw IllegalStateException("Unsupported platform: ${currentPlatform.name}")
        }
    }
}
