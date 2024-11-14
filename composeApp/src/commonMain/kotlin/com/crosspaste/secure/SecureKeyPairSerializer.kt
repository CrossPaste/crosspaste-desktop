package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA

class SecureKeyPairSerializer {
    private val provider = CryptographyProvider.Default
    private val ecdsa = provider.get(ECDSA)
    private val ecdh = provider.get(ECDH)
    private val signPublicKeyDecoder = ecdsa.publicKeyDecoder(EC.Curve.P256)
    private val signPrivateKeyDecoder = ecdsa.privateKeyDecoder(EC.Curve.P256)
    private val cryptPublicKeyDecoder = ecdh.publicKeyDecoder(EC.Curve.P256)
    private val cryptPrivateKeyDecoder = ecdh.privateKeyDecoder(EC.Curve.P256)

    fun encodeSignPublicKey(publicKey: ECDSA.PublicKey): ByteArray {
        return publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.DER)
    }

    fun decodeSignPublicKey(bytes: ByteArray): ECDSA.PublicKey {
        return signPublicKeyDecoder.decodeFromByteArrayBlocking(
            format = EC.PublicKey.Format.DER,
            bytes = bytes
        )
    }

    fun encodeSignPrivateKey(privateKey: ECDSA.PrivateKey): ByteArray {
        return privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER)
    }

    fun decodeSignPrivateKey(bytes: ByteArray): ECDSA.PrivateKey {
        return signPrivateKeyDecoder.decodeFromByteArrayBlocking(
            format = EC.PrivateKey.Format.DER,
            bytes = bytes
        )
    }

    fun encodeSignKeyPair(keyPair: ECDSA.KeyPair): ByteArray {
        val publicKeyBytes = encodeSignPublicKey(keyPair.publicKey)
        val privateKeyBytes = encodeSignPrivateKey(keyPair.privateKey)

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

    fun decodeSignKeyPair(bytes: ByteArray): ECDSA.KeyPair {
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
            publicKey = decodeSignPublicKey(publicKeyBytes),
            privateKey = decodeSignPrivateKey(privateKeyBytes),
        )
    }

    fun encodeCryptPublicKey(publicKey: ECDH.PublicKey): ByteArray {
        return publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.DER)
    }

    fun decodeCryptPublicKey(bytes: ByteArray): ECDH.PublicKey {
        return cryptPublicKeyDecoder.decodeFromByteArrayBlocking(
            format = EC.PublicKey.Format.DER,
            bytes = bytes
        )
    }

    fun encodeCryptPrivateKey(privateKey: ECDH.PrivateKey): ByteArray {
        return privateKey.encodeToByteArrayBlocking(EC.PrivateKey.Format.DER)
    }

    fun decodeCryptPrivateKey(bytes: ByteArray): ECDH.PrivateKey {
        return cryptPrivateKeyDecoder.decodeFromByteArrayBlocking(
            format = EC.PrivateKey.Format.DER,
            bytes = bytes
        )
    }

    fun encodeCryptKeyPair(keyPair: ECDH.KeyPair): ByteArray {
        val publicKeyBytes = encodeCryptPublicKey(keyPair.publicKey)
        val privateKeyBytes = encodeCryptPrivateKey(keyPair.privateKey)

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

    fun decodeCryptKeyPair(bytes: ByteArray): ECDH.KeyPair {
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

        return ECDHKeyPairImpl(
            publicKey = decodeCryptPublicKey(publicKeyBytes),
            privateKey = decodeCryptPrivateKey(privateKeyBytes),
        )
    }

    fun decodeSecureKeyPair(bytes: ByteArray): SecureKeyPair {
        var offset = 0

        // Read sign key pair size and content
        val signKeyPairSize = readIntFromByteArray(bytes, offset)
        offset += 4
        val signKeyPairBytes = bytes.sliceArray(offset until offset + signKeyPairSize)
        offset += signKeyPairSize

        // Read crypt key pair size and content
        val cryptKeyPairSize = readIntFromByteArray(bytes, offset)
        offset += 4
        val cryptKeyPairBytes = bytes.sliceArray(offset until offset + cryptKeyPairSize)

        // Decode both key pairs
        val signKeyPair = decodeSignKeyPair(signKeyPairBytes)
        val cryptKeyPair = decodeCryptKeyPair(cryptKeyPairBytes)

        return SecureKeyPair(signKeyPair, cryptKeyPair)
    }

    fun encodeSecureKeyPair(keyPair: SecureKeyPair): ByteArray {
        val signKeyPairBytes = encodeSignKeyPair(keyPair.signKeyPair)
        val cryptKeyPairBytes = encodeCryptKeyPair(keyPair.cryptKeyPair)

        // Calculate total size needed:
        // 4 bytes for sign key pair size + sign key pair bytes
        // 4 bytes for crypt key pair size + crypt key pair bytes
        val totalSize = 4 + signKeyPairBytes.size + 4 + cryptKeyPairBytes.size

        return ByteArray(totalSize).apply {
            var offset = 0

            // Write sign key pair size and content
            writeIntToByteArray(this, signKeyPairBytes.size, offset)
            offset += 4
            signKeyPairBytes.copyInto(this, offset)
            offset += signKeyPairBytes.size

            // Write crypt key pair size and content
            writeIntToByteArray(this, cryptKeyPairBytes.size, offset)
            offset += 4
            cryptKeyPairBytes.copyInto(this, offset)
        }
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

@OptIn(CryptographyProviderApi::class)
class ECDHKeyPairImpl(
    override val publicKey: ECDH.PublicKey,
    override val privateKey: ECDH.PrivateKey,
) : ECDH.KeyPair