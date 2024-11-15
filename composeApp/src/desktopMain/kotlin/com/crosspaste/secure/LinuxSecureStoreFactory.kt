package com.crosspaste.secure

import com.crosspaste.app.AppFileType
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.realm.secure.SecureRealm
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class LinuxSecureStoreFactory(
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureRealm: SecureRealm,
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
                    val secureKeyPair = secureKeyPairSerializer.decodeSecureKeyPair(it)
                    return@createSecureStore SecureStore(secureKeyPair, secureKeyPairSerializer, secureRealm)
                } catch (e: Exception) {
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
        val secureKeyPair = generateSecureKeyPair()
        val data = secureKeyPairSerializer.encodeSecureKeyPair(secureKeyPair)
        filePersist.saveBytes(data)
        val permissions = PosixFilePermissions.fromString("rw-------")
        Files.setPosixFilePermissions(filePersist.path.toNioPath(), permissions)
        return SecureStore(secureKeyPair, secureKeyPairSerializer, secureRealm)
    }
}