package com.crosspaste.signal

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.macos.MacosKeychainHelper
import com.crosspaste.platform.windows.WindowDapiHelper
import com.crosspaste.presist.FilePersist
import com.crosspaste.realm.signal.SignalRealm
import com.crosspaste.utils.EncryptUtils
import com.crosspaste.utils.getAppEnvUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.util.KeyHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class DesktopIdentityKeyStore(
    private val signalRealm: SignalRealm,
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int,
) : IdentityKeyStore {
    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        return registrationId
    }

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): Boolean {
        return signalRealm.saveIdentity(address.name, identityKey.serialize())
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val identity: IdentityKey? = getIdentity(address)
        return identity?.let {
            return (it == identityKey)
        } ?: false
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return signalRealm.identity(address.name)?.let {
            IdentityKey(it)
        }
    }
}

fun getPasteIdentityKeyStoreFactory(
    appInfo: AppInfo,
    signalRealm: SignalRealm,
): IdentityKeyStoreFactory {
    val currentPlatform = getPlatform()
    return if (currentPlatform.isMacos()) {
        MacosIdentityKeyStoreFactory(appInfo, signalRealm)
    } else if (currentPlatform.isWindows()) {
        WindowsIdentityKeyStoreFactory(signalRealm)
    } else if (currentPlatform.isLinux()) {
        LinuxIdentityKeyStoreFactory(signalRealm)
    } else {
        throw IllegalStateException("Unknown platform: ${currentPlatform.name}")
    }
}

private data class IdentityKeyPairWithRegistrationId(
    val identityKeyPair: IdentityKeyPair,
    val registrationId: Int,
)

private fun readIdentityKeyPairWithRegistrationId(data: ByteArray): IdentityKeyPairWithRegistrationId {
    val byteArrayInputStream = ByteArrayInputStream(data)
    val inputStream = DataInputStream(byteArrayInputStream)
    val byteSize = inputStream.readInt()
    val byteArray = ByteArray(byteSize)
    inputStream.read(byteArray)
    val identityKeyPair = IdentityKeyPair(byteArray)
    val registrationId = inputStream.readInt()
    return IdentityKeyPairWithRegistrationId(identityKeyPair, registrationId)
}

private fun writeIdentityKeyPairWithRegistrationId(
    identityKeyPair: IdentityKeyPair,
    registrationId: Int,
): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val dataOutputStream = DataOutputStream(byteArrayOutputStream)
    val identityKeyPairBytes = identityKeyPair.serialize()
    dataOutputStream.writeInt(identityKeyPairBytes.size)
    dataOutputStream.write(identityKeyPairBytes)
    dataOutputStream.writeInt(registrationId)
    return byteArrayOutputStream.toByteArray()
}

class MacosIdentityKeyStoreFactory(
    private val appInfo: AppInfo,
    private val signalRealm: SignalRealm,
) : IdentityKeyStoreFactory {

    private val logger = KotlinLogging.logger {}

    private val appEnvUtils = getAppEnvUtils()

    private val filePersist =
        FilePersist.createOneFilePersist(
            DesktopAppPathProvider.resolve("signal.data", AppFileType.ENCRYPT),
        )

    override fun createIdentityKeyStore(): IdentityKeyStore {
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
                    val (identityKeyPair, registrationId) = readIdentityKeyPairWithRegistrationId(decryptData)
                    return@createIdentityKeyStore DesktopIdentityKeyStore(signalRealm, identityKeyPair, registrationId)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt signalProtocol" }
                }
            }

            if (file.delete()) {
                logger.info { "Delete identityKey encrypt file" }
            }
        } else {
            logger.info { "No found identityKey encrypt file" }
        }

        logger.info { "Creating new identityKey" }
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val data = writeIdentityKeyPairWithRegistrationId(identityKeyPair, registrationId)
        val password = MacosKeychainHelper.getPassword(service, appInfo.userName)

        val secretKey =
            password?.let {
                logger.info { "Found password in keychain by $service ${appInfo.userName}" }
                EncryptUtils.stringToSecretKey(it)
            } ?: run {
                logger.info { "Generating new password in keychain by $service ${appInfo.userName}" }
                val secretKey = EncryptUtils.generateAESKey()
                MacosKeychainHelper.setPassword(service, appInfo.userName, EncryptUtils.secretKeyToString(secretKey))
                secretKey
            }

        val encryptData = EncryptUtils.encryptData(secretKey, data)
        filePersist.saveBytes(encryptData)
        return DesktopIdentityKeyStore(signalRealm, identityKeyPair, registrationId)
    }
}

class WindowsIdentityKeyStoreFactory(
    private val signalRealm: SignalRealm,
) : IdentityKeyStoreFactory {

    private val logger = KotlinLogging.logger {}

    private val filePersist =
        FilePersist.createOneFilePersist(
            DesktopAppPathProvider.resolve("signal.data", AppFileType.ENCRYPT),
        )

    override fun createIdentityKeyStore(): IdentityKeyStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found identityKey encrypt file" }
            filePersist.readBytes()?.let {
                try {
                    val decryptData = WindowDapiHelper.decryptData(it)
                    decryptData?.let { byteArray ->
                        val (identityKeyPair, registrationId) = readIdentityKeyPairWithRegistrationId(byteArray)
                        return@createIdentityKeyStore DesktopIdentityKeyStore(signalRealm, identityKeyPair, registrationId)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt identityKey" }
                }
            }
            if (file.delete()) {
                logger.info { "Delete identityKey encrypt file" }
            }
        } else {
            logger.info { "No found identityKey encrypt file" }
        }

        logger.info { "Creating new identityKey" }
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val data = writeIdentityKeyPairWithRegistrationId(identityKeyPair, registrationId)
        val encryptData = WindowDapiHelper.encryptData(data)
        filePersist.saveBytes(encryptData!!)
        return DesktopIdentityKeyStore(signalRealm, identityKeyPair, registrationId)
    }
}

class LinuxIdentityKeyStoreFactory(
    private val signalRealm: SignalRealm,
) : IdentityKeyStoreFactory {

    private val logger = KotlinLogging.logger {}

    private val filePersist =
        FilePersist.createOneFilePersist(
            DesktopAppPathProvider.resolve("signal.data", AppFileType.ENCRYPT),
        )

    override fun createIdentityKeyStore(): IdentityKeyStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found identityKey encrypt file" }
            filePersist.readBytes()?.let {
                try {
                    val (identityKeyPair, registrationId) = readIdentityKeyPairWithRegistrationId(it)
                    return@createIdentityKeyStore DesktopIdentityKeyStore(signalRealm, identityKeyPair, registrationId)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to read identityKey" }
                }
            }
            if (file.delete()) {
                logger.info { "Delete identityKey encrypt file" }
            }
        } else {
            logger.info { "No found identityKey encrypt file" }
        }

        logger.info { "Creating new identityKey" }
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val data = writeIdentityKeyPairWithRegistrationId(identityKeyPair, registrationId)
        filePersist.saveBytes(data)
        val permissions = PosixFilePermissions.fromString("rw-------")
        Files.setPosixFilePermissions(filePersist.path.toNioPath(), permissions)
        return DesktopIdentityKeyStore(signalRealm, identityKeyPair, registrationId)
    }
}
