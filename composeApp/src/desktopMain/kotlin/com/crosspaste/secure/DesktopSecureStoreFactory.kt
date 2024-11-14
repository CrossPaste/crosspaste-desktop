package com.crosspaste.secure

import com.crosspaste.app.AppInfo
import com.crosspaste.platform.getPlatform
import com.crosspaste.realm.secure.SecureRealm

class DesktopSecureStoreFactory(
    private val appInfo: AppInfo,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureRealm: SecureRealm,
) : SecureStoreFactory {

    private val currentPlatform = getPlatform()

    override fun createSecureStore(): SecureStore {
        return getSecureStoreFactory().createSecureStore()
    }

    private fun getSecureStoreFactory(): SecureStoreFactory {
        return if (currentPlatform.isMacos()) {
            MacosSecureStoreFactory(appInfo, secureKeyPairSerializer, secureRealm)
        } else if (currentPlatform.isWindows()) {
            WindowsSecureStoreFactory(secureKeyPairSerializer, secureRealm)
        } else if (currentPlatform.isLinux()) {
            LinuxSecureStoreFactory(secureKeyPairSerializer, secureRealm)
        } else {
            throw IllegalStateException("Unsupported platform: ${currentPlatform.name}")
        }
    }
}
