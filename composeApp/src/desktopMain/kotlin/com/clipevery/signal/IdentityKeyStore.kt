package com.clipevery.signal

import com.clipevery.Database
import com.clipevery.app.AppFileType
import com.clipevery.utils.decryptData
import com.clipevery.utils.encryptData
import com.clipevery.utils.generateAESKey
import com.clipevery.utils.secretKeyToString
import com.clipevery.utils.stringToSecretKey
import com.clipevery.os.macos.MacosKeychainHelper
import com.clipevery.app.AppInfo
import com.clipevery.path.getPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.os.windows.WindowDapiHelper
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

class DesktopIdentityKeyStore(private val database: Database,
                              private val appInstanceId: String,
                              private val identityKeyPair: IdentityKeyPair,
                              private val registrationId: Int): IdentityKeyStore {
    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        return registrationId
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        database.identityKeyQueries.selectIndentity(appInstanceId).executeAsOneOrNull()?.let {
            database.identityKeyQueries.update(identityKey.serialize(), address.name)
            return true
        } ?: let {
            database.identityKeyQueries.insert(address.name, identityKey.serialize())
            return false
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        val identity: IdentityKey? = getIdentity(address)
        return identity?.let { it == identityKey } ?: false
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return database.identityKeyQueries.selectIndentity(address.name)
            .executeAsOneOrNull()?.let {
                IdentityKey(it)
            }
    }

}

val logger = KotlinLogging.logger {}

fun getClipIdentityKeyStoreFactory(appInfo: AppInfo,
                                   database: Database): IdentityKeyStoreFactory {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosIdentityKeyStoreFactory(appInfo, database)
    } else if (currentPlatform.isWindows()) {
        WindowsIdentityKeyStoreFactory(appInfo, database)
    } else {
        throw IllegalStateException("Unknown platform: ${currentPlatform.name}")
    }
}

private data class IdentityKeyPairWithRegistrationId(val identityKeyPair: IdentityKeyPair,
                                                     val registrationId: Int)

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

private fun writeIdentityKeyPairWithRegistrationId(identityKeyPair: IdentityKeyPair, registrationId: Int): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val dataOutputStream = DataOutputStream(byteArrayOutputStream)
    val identityKeyPairBytes = identityKeyPair.serialize()
    dataOutputStream.writeInt(identityKeyPairBytes.size)
    dataOutputStream.write(identityKeyPairBytes)
    dataOutputStream.writeInt(registrationId)
    return byteArrayOutputStream.toByteArray()
}

class MacosIdentityKeyStoreFactory(private val appInfo: AppInfo,
                                   private val database: Database): IdentityKeyStoreFactory {

    private val filePersist = DesktopOneFilePersist(
        getPathProvider()
        .resolve("signal.data", AppFileType.ENCRYPT))

    override fun createIdentityKeyStore(): IdentityKeyStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found ideIdentityKey encrypt file" }
            val bytes = file.readBytes()
            val password = MacosKeychainHelper.getPassword(appInfo.appInstanceId, appInfo.userName)

            password?.let {
                logger.info { "Found password in keychain by ${appInfo.appInstanceId} ${appInfo.userName}" }
                try {
                    val secretKey = stringToSecretKey(it)
                    val decryptData = decryptData(secretKey, bytes)
                    val (identityKeyPair, registrationId) = readIdentityKeyPairWithRegistrationId(decryptData)
                    return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt signalProtocol" }
                }
            }

            if (file.delete()) {
                logger.info { "Delete ideIdentityKey encrypt file" }
            }

        } else {
            logger.info { "No found ideIdentityKey encrypt file"  }
        }

        logger.info { "Creating new ideIdentityKey" }
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val data = writeIdentityKeyPairWithRegistrationId(identityKeyPair, registrationId)
        val password = MacosKeychainHelper.getPassword(appInfo.appInstanceId, appInfo.userName)

        val secretKey = password?.let {
            logger.info { "Found password in keychain by ${appInfo.appInstanceId} ${appInfo.userName}" }
            stringToSecretKey(it)
        } ?: run {
            logger.info { "Generating new password in keychain by ${appInfo.appInstanceId} ${appInfo.userName}" }
            val secretKey = generateAESKey()
            MacosKeychainHelper.setPassword(appInfo.appInstanceId, appInfo.userName, secretKeyToString(secretKey))
            secretKey
        }

        val encryptData = encryptData(secretKey, data)
        filePersist.saveBytes(encryptData)
        return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId)
    }
}


class WindowsIdentityKeyStoreFactory(private val appInfo: AppInfo,
                                     private val database: Database) : IdentityKeyStoreFactory {

    private val filePersist = DesktopOneFilePersist(
        getPathProvider()
        .resolve("signal.data", AppFileType.ENCRYPT))

    override fun createIdentityKeyStore(): IdentityKeyStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found ideIdentityKey encrypt file" }
            filePersist.readBytes()?.let {
                try {
                    val decryptData = WindowDapiHelper.decryptData(it)
                    decryptData?.let { byteArray ->
                        val (identityKeyPair, registrationId) = readIdentityKeyPairWithRegistrationId(byteArray)
                        return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId)
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decrypt ideIdentityKey" }
                }
            }
            if (file.delete()) {
                logger.info { "Delete ideIdentityKey encrypt file" }
            }
        } else {
            logger.info { "No found ideIdentityKey encrypt file"  }
        }

        logger.info { "Creating new ideIdentityKey" }
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        val data = writeIdentityKeyPairWithRegistrationId(identityKeyPair, registrationId)
        val encryptData = WindowDapiHelper.encryptData(data)
        filePersist.saveBytes(encryptData!!)
        return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId)
    }
}