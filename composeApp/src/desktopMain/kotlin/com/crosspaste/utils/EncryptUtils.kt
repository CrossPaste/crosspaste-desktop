package com.crosspaste.utils

import com.crosspaste.dao.signal.PastePreKey
import com.crosspaste.dao.signal.PasteSignedPreKey
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.utils.DesktopCodecsUtils.base64Decode
import com.crosspaste.utils.DesktopCodecsUtils.base64Encode
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.Medium
import java.security.SecureRandom
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptUtils {

    @Synchronized
    fun generatePreKeyPair(signalDao: SignalDao): PastePreKey {
        val preKeyPair = Curve.generateKeyPair()
        val random = Random()
        var preKeyId: Int
        do {
            preKeyId = random.nextInt(Medium.MAX_VALUE)
        } while (signalDao.existPreKey(preKeyId))
        val preKeyRecord = PreKeyRecord(preKeyId, preKeyPair)
        val serialize = preKeyRecord.serialize()
        signalDao.storePreKey(preKeyId, serialize)
        return PastePreKey(preKeyId, serialize)
    }

    @Synchronized
    fun generatesSignedPreKeyPair(
        signalDao: SignalDao,
        privateKey: ECPrivateKey,
    ): PasteSignedPreKey {
        val random = Random()
        val signedPreKeyId = random.nextInt(Medium.MAX_VALUE)

        signalDao.getSignedPreKey(signedPreKeyId)?.let { signedPreKey ->
            return signedPreKey
        } ?: run {
            val signedPreKeyPair = Curve.generateKeyPair()
            val signedPreKeySignature =
                Curve.calculateSignature(
                    privateKey,
                    signedPreKeyPair.publicKey.serialize(),
                )
            val signedPreKeyRecord =
                SignedPreKeyRecord(
                    signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature,
                )
            signalDao.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord.serialize())
            return PasteSignedPreKey(signedPreKeyId, signedPreKeyRecord.serialize())
        }
    }

    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun secretKeyToString(secretKey: SecretKey): String {
        val encodedKey = secretKey.encoded
        return base64Encode(encodedKey)
    }

    fun stringToSecretKey(encodedKey: String): SecretKey {
        val decodedKey = base64Decode(encodedKey)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
    }

    fun encryptData(
        key: SecretKey,
        data: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = ByteArray(cipher.blockSize)
        SecureRandom().nextBytes(ivBytes)
        val ivSpec = IvParameterSpec(ivBytes)

        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val encrypted = cipher.doFinal(data)
        return ivBytes + encrypted
    }

    fun decryptData(
        key: SecretKey,
        encryptedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = encryptedData.copyOfRange(0, 16)
        val actualEncryptedData = encryptedData.copyOfRange(16, encryptedData.size)

        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

        return cipher.doFinal(actualEncryptedData)
    }
}
