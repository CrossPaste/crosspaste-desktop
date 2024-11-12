package com.crosspaste.secure

import com.crosspaste.app.AppFileType
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.windows.WindowDapiHelper
import com.crosspaste.presist.FilePersist
import com.crosspaste.realm.secure.SecureRealm
import io.github.oshai.kotlinlogging.KotlinLogging

class WindowsSecureStoreFactory(
    private val ecdsaSerializer: ECDSASerializer,
    private val secureRealm: SecureRealm,
): SecureStoreFactory {
    private val logger = KotlinLogging.logger {}

    private val filePersist =
        FilePersist.createOneFilePersist(
            DesktopAppPathProvider.resolve("secure.data", AppFileType.ENCRYPT),
        )

    override fun createSecureStore(): SecureStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found identityKey encrypt file" }
            filePersist.readBytes()?.let {
                try {
                    val decryptData = WindowDapiHelper.decryptData(it)
                    decryptData?.let { byteArray ->
                        val identityKeyPair = ecdsaSerializer.decodeKeyPair(byteArray)
                        return@createSecureStore SecureStore(identityKeyPair, ecdsaSerializer, secureRealm)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt identityKey" }
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
        val encryptData = WindowDapiHelper.encryptData(data)
        filePersist.saveBytes(encryptData!!)
        return SecureStore(identityKeyPair, ecdsaSerializer, secureRealm)
    }
}