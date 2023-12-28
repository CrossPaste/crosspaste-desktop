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
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.protocol.util.Medium
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Random

class DesktopIdentityKeyStore(private val database: Database,
                              private val appInstanceId: String,
                              private val identityKeyPair: IdentityKeyPair,
                              private val registrationId: Int,
                              private val preKeyId: Int,
                              private val signedPreKeyId: Int): ClipIdentityKeyStore {
    override fun getIdentityKeyPair(): IdentityKeyPair {
        return identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        return registrationId
    }

    override fun getPreKeyId(): Int {
        return preKeyId
    }

    override fun getSignedPreKeyId(): Int {
        return signedPreKeyId
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
                                   database: Database,
                                   preKeyStore: PreKeyStore,
                                   signedPreKeyStore: SignedPreKeyStore): ClipIdentityKeyStoreFactory {
    val currentPlatform = currentPlatform()
    return if (currentPlatform.isMacos()) {
        MacosClipIdentityKeyStoreFactory(appInfo, database, preKeyStore, signedPreKeyStore)
    } else if (currentPlatform.isWindows()) {
        WindowsClipIdentityKeyStoreFactory(appInfo, database, preKeyStore, signedPreKeyStore)
    } else {
        throw IllegalStateException("Unknown platform: ${currentPlatform.name}")
    }
}

private data class IdentityKeyPairWithOtherKeyId(val identityKeyPair: IdentityKeyPair,
                                                 val registrationId: Int,
                                                 val preKeyId: Int,
                                                 val signedPreKeyId: Int)

private fun readIdentityKeyPairWithOtherKeyId(data: ByteArray): IdentityKeyPairWithOtherKeyId {
    val byteArrayInputStream = ByteArrayInputStream(data)
    val inputStream = DataInputStream(byteArrayInputStream)
    val byteSize = inputStream.readInt()
    val byteArray = ByteArray(byteSize)
    inputStream.read(byteArray)
    val identityKeyPair = IdentityKeyPair(byteArray)
    val registrationId = inputStream.readInt()
    val preKeyId = inputStream.readInt()
    val signedPreKeyId = inputStream.readInt()
    return IdentityKeyPairWithOtherKeyId(identityKeyPair, registrationId, preKeyId, signedPreKeyId)
}

private fun writeIdentityKeyPairWithOtherKeyId(identityKeyPair: IdentityKeyPair, registrationId: Int, preKeyId: Int, signedPreKeyId: Int): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val dataOutputStream = DataOutputStream(byteArrayOutputStream)
    val identityKeyPairBytes = identityKeyPair.serialize()
    dataOutputStream.writeInt(identityKeyPairBytes.size)
    dataOutputStream.write(identityKeyPairBytes)
    dataOutputStream.writeInt(registrationId)
    dataOutputStream.writeInt(preKeyId)
    dataOutputStream.writeInt(signedPreKeyId)
    return byteArrayOutputStream.toByteArray()
}

class MacosClipIdentityKeyStoreFactory(private val appInfo: AppInfo,
                                       private val database: Database,
                                       private val preKeyStore: PreKeyStore,
                                       private val signedPreKeyStore: SignedPreKeyStore): ClipIdentityKeyStoreFactory {

    private val filePersist = DesktopOneFilePersist(
        getPathProvider()
        .resolve("signal.data", AppFileType.ENCRYPT))

    override fun createClipIdentityKeyStore(): ClipIdentityKeyStore {
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
                    val (identityKeyPair, registrationId, preKeyId, signedPreKeyId) = readIdentityKeyPairWithOtherKeyId(decryptData)
                    return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId, preKeyId, signedPreKeyId)
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
        val preKeyPair = Curve.generateKeyPair()
        val signedPreKeyPair = Curve.generateKeyPair()
        val signedPreKeySignature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            signedPreKeyPair.publicKey.serialize()
        )
        val random = Random()
        val preKeyId = random.nextInt(Medium.MAX_VALUE)
        val signedPreKeyId = random.nextInt(Medium.MAX_VALUE)
        preKeyStore.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKeyPair))
        signedPreKeyStore.storeSignedPreKey(signedPreKeyId,
            SignedPreKeyRecord(
                signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature
            )
        )
        val data = writeIdentityKeyPairWithOtherKeyId(identityKeyPair, registrationId, preKeyId, signedPreKeyId)
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
        return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId, preKeyId, signedPreKeyId)
    }
}


class WindowsClipIdentityKeyStoreFactory(private val appInfo: AppInfo,
                                         private val database: Database,
                                         private val preKeyStore: PreKeyStore,
                                         private val signedPreKeyStore: SignedPreKeyStore) : ClipIdentityKeyStoreFactory {

    private val filePersist = DesktopOneFilePersist(
        getPathProvider()
        .resolve("signal.data", AppFileType.ENCRYPT))

    override fun createClipIdentityKeyStore(): ClipIdentityKeyStore {
        val file = filePersist.path.toFile()
        if (file.exists()) {
            logger.info { "Found ideIdentityKey encrypt file" }
            filePersist.readBytes()?.let {
                try {
                    val decryptData = WindowDapiHelper.decryptData(it)
                    decryptData?.let { byteArray ->
                        val (identityKeyPair, registrationId, preKeyId, signedPreKeyId) = readIdentityKeyPairWithOtherKeyId(byteArray)
                        return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId, preKeyId, signedPreKeyId)
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
        val preKeyPair = Curve.generateKeyPair()
        val signedPreKeyPair = Curve.generateKeyPair()
        val signedPreKeySignature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            signedPreKeyPair.publicKey.serialize()
        )
        val random = Random()
        val preKeyId = random.nextInt(Medium.MAX_VALUE)
        val signedPreKeyId = random.nextInt(Medium.MAX_VALUE)
        preKeyStore.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKeyPair))
        signedPreKeyStore.storeSignedPreKey(signedPreKeyId,
            SignedPreKeyRecord(
                signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature
            )
        )
        val data = writeIdentityKeyPairWithOtherKeyId(identityKeyPair, registrationId, preKeyId, signedPreKeyId)
        val encryptData = WindowDapiHelper.encryptData(data)
        filePersist.saveBytes(encryptData!!)
        return DesktopIdentityKeyStore(database, appInfo.appInstanceId, identityKeyPair, registrationId, preKeyId, signedPreKeyId)
    }
}