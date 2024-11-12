package com.crosspaste.secure

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.macos.MacosKeychainHelper
import com.crosspaste.presist.FilePersist
import com.crosspaste.realm.secure.SecureRealm
import com.crosspaste.utils.EncryptUtils
import com.crosspaste.utils.getAppEnvUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class MacosSecureStoreFactory(
    private val appInfo: AppInfo,
    private val ecdsaSerializer: ECDSASerializer,
    private val secureRealm: SecureRealm,
): SecureStoreFactory {

    private val logger = KotlinLogging.logger {}

    private val appEnvUtils = getAppEnvUtils()

    private val filePersist =
        FilePersist.createOneFilePersist(
            DesktopAppPathProvider.resolve("secure.data", AppFileType.ENCRYPT),
        )

    override fun createSecureStore(): SecureStore {
        val service = "crosspaste-${appEnvUtils.getCurrentAppEnv().name}-${appInfo.appInstanceId}"
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found identityKey encrypt file" }
            val bytes = file.readBytes()
            val password = MacosKeychainHelper.getPassword(service, appInfo.userName)
            password?.let {
                logger.info { "Found password in keychain by $service ${appInfo.userName}" }
                try {
                    val secretKey = EncryptUtils.stringToSecretKey(it)
                    val decryptData = EncryptUtils.decryptData(secretKey, bytes)
                    val identityKeyPair = ecdsaSerializer.decodeKeyPair(decryptData)
                    return@createSecureStore SecureStore(identityKeyPair, ecdsaSerializer, secureRealm)
                } catch (e: Exception) {
                    logger.error(e) { "Decrypt identityKey error" }
                }
            }

            if (file.delete()) {
                logger.info { "Delete identityKey encrypt file" }
            }
        } else {
            logger.info { "Not found identityKey encrypt file" }
        }

        logger.info { "Generate identityKeyPair" }
        val identityKeyPair = generateIdentityKeyPair()
        val data = ecdsaSerializer.encodeKeyPair(identityKeyPair)
        val password = MacosKeychainHelper.getPassword(service, appInfo.userName)

        val secretKey = password?.let {
            logger.info { "Found password in keychain by $service ${appInfo.userName}" }
            EncryptUtils.stringToSecretKey(it)
        } ?: run {
            logger.info { "Not found password in keychain by $service ${appInfo.userName}" }
            val secretKey = EncryptUtils.generateAESKey()
            MacosKeychainHelper.setPassword(service, appInfo.userName, EncryptUtils.secretKeyToString(secretKey))
            secretKey
        }
        val encryptData = EncryptUtils.encryptData(secretKey, data)
        filePersist.saveBytes(encryptData)
        return SecureStore(identityKeyPair, ecdsaSerializer, secureRealm)
    }
}