package com.crosspaste.secure

import com.crosspaste.app.AppFileType
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.windows.WindowDapiHelper
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.CryptographyUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class WindowsSecureStoreFactory(
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureIO: SecureIO,
) : SecureStoreFactory {
    private val logger = KotlinLogging.logger {}

    private val filePersist =
        FilePersist.createOneFilePersist(
            DesktopAppPathProvider.resolve("secure.data", AppFileType.ENCRYPT),
        )

    override fun createSecureStore(): SecureStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found secureKeyPair encrypt file" }
            filePersist.readBytes()?.let {
                try {
                    val decryptData = WindowDapiHelper.decryptData(it)
                    decryptData?.let { byteArray ->
                        val secureKeyPair = secureKeyPairSerializer.decodeSecureKeyPair(byteArray)
                        return@createSecureStore GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt secureKeyPair" }
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
        val encryptData = WindowDapiHelper.encryptData(data)
        filePersist.saveBytes(encryptData!!)
        return GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)
    }
}
