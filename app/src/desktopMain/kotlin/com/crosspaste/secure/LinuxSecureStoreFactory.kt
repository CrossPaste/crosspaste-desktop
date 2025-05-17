package com.crosspaste.secure

import com.crosspaste.app.AppFileType
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.path.AppPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.CryptographyUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class LinuxSecureStoreFactory(
    appPathProvider: AppPathProvider,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureIO: SecureIO,
) : SecureStoreFactory {

    private val logger = KotlinLogging.logger {}

    private val filePersist =
        FilePersist.createOneFilePersist(
            appPathProvider.resolve("secure.data", AppFileType.ENCRYPT),
        )

    override fun createSecureStore(): SecureStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found secureKeyPair encrypt file" }
            filePersist.readBytes()?.let {
                runCatching {
                    val secureKeyPair = secureKeyPairSerializer.decodeSecureKeyPair(it)
                    return@createSecureStore GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)
                }.onFailure { e ->
                    logger.error(e) { "Failed to read secureKeyPair" }
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
        filePersist.saveBytes(data)
        val permissions = PosixFilePermissions.fromString("rw-------")
        Files.setPosixFilePermissions(filePersist.path.toNioPath(), permissions)
        return GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)
    }
}
