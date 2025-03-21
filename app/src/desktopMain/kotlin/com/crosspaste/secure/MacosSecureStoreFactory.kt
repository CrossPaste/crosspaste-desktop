package com.crosspaste.secure

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.macos.MacosKeychainHelper
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.EncryptUtils
import com.crosspaste.utils.getAppEnvUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class MacosSecureStoreFactory(
    private val appInfo: AppInfo,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureIO: SecureIO,
) : SecureStoreFactory {

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
            logger.info { "Found secureKeyPair encrypt file" }
            val bytes = file.readBytes()
            val password = MacosKeychainHelper.getPassword(service, appInfo.userName)
            password?.let {
                logger.info { "Found password in keychain by $service ${appInfo.userName}" }
                runCatching {
                    val secretKey = EncryptUtils.stringToSecretKey(it)
                    val decryptData = EncryptUtils.decryptData(secretKey, bytes)
                    val secureKeyPair = secureKeyPairSerializer.decodeSecureKeyPair(decryptData)
                    return@createSecureStore GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)
                }.onFailure { e ->
                    logger.error(e) { "Decrypt secureKeyPair error" }
                }
            }

            if (file.delete()) {
                logger.info { "Delete secureKeyPair encrypt file" }
            }
        } else {
            logger.info { "Not found secureKeyPair encrypt file" }
        }

        logger.info { "Generate secureKeyPair" }
        val secureKeyPair = CryptographyUtils.generateSecureKeyPair()
        val data = secureKeyPairSerializer.encodeSecureKeyPair(secureKeyPair)
        val password = MacosKeychainHelper.getPassword(service, appInfo.userName)

        val secretKey =
            password?.let {
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
        return GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)
    }
}
