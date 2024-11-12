package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA

class ECDSASerializer {
    private val provider = CryptographyProvider.Default
    private val ecdsa = provider.get(ECDSA)
    private val publicKeyDecoder = ecdsa.publicKeyDecoder(EC.Curve.P256)
    private val privateKeyDecoder = ecdsa.privateKeyDecoder(EC.Curve.P256)

    fun encodePublicKey(publicKey: ECDSA.PublicKey): ByteArray {
        return publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.DER)
    }

    fun decodePublicKey(bytes: ByteArray): ECDSA.PublicKey {
        return publicKeyDecoder.decodeFromByteArrayBlocking(
            format = EC.PublicKey.Format.DER,
            bytes = bytes
        )
    }

    fun encodePrivateKey(privateKey: ECDSA.PrivateKey): ByteArray {
        return privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER)
    }

    fun decodePrivateKey(bytes: ByteArray): ECDSA.PrivateKey {
        return privateKeyDecoder.decodeFromByteArrayBlocking(
            format = EC.PrivateKey.Format.DER,
            bytes = bytes
        )
    }

    fun encodeKeyPair(keyPair: ECDSA.KeyPair): ByteArray {
        val publicKeyBytes = encodePublicKey(keyPair.publicKey)
        val privateKeyBytes = encodePrivateKey(keyPair.privateKey)

        // Calculate total size needed:
        // 4 bytes for public key size + public key bytes
        // 4 bytes for private key size + private key bytes
        val totalSize = 4 + publicKeyBytes.size + 4 + privateKeyBytes.size

        return ByteArray(totalSize).apply {
            var offset = 0

            // Write public key size and content
            writeIntToByteArray(this, publicKeyBytes.size, offset)
            offset += 4
            publicKeyBytes.copyInto(this, offset)
            offset += publicKeyBytes.size

            // Write private key size and content
            writeIntToByteArray(this, privateKeyBytes.size, offset)
            offset += 4
            privateKeyBytes.copyInto(this, offset)
        }
    }

    fun decodeKeyPair(bytes: ByteArray): ECDSA.KeyPair {
        var offset = 0

        // Read public key size and content
        val publicKeySize = readIntFromByteArray(bytes, offset)
        offset += 4
        val publicKeyBytes = bytes.sliceArray(offset until offset + publicKeySize)
        offset += publicKeySize

        // Read private key size and content
        val privateKeySize = readIntFromByteArray(bytes, offset)
        offset += 4
        val privateKeyBytes = bytes.sliceArray(offset until offset + privateKeySize)

        return ECDSAKeyPairImpl(
            publicKey = decodePublicKey(publicKeyBytes),
            privateKey = decodePrivateKey(privateKeyBytes),
        )
    }

    private fun writeIntToByteArray(dest: ByteArray, value: Int, offset: Int) {
        dest[offset] = (value shr 24).toByte()
        dest[offset + 1] = (value shr 16).toByte()
        dest[offset + 2] = (value shr 8).toByte()
        dest[offset + 3] = value.toByte()
    }

    private fun readIntFromByteArray(src: ByteArray, offset: Int): Int {
        return ((src[offset].toInt() and 0xFF) shl 24) or
                ((src[offset + 1].toInt() and 0xFF) shl 16) or
                ((src[offset + 2].toInt() and 0xFF) shl 8) or
                (src[offset + 3].toInt() and 0xFF)
    }
}

@OptIn(CryptographyProviderApi::class)
class ECDSAKeyPairImpl(
    override val publicKey: ECDSA.PublicKey,
    override val privateKey: ECDSA.PrivateKey,
) : ECDSA.KeyPair