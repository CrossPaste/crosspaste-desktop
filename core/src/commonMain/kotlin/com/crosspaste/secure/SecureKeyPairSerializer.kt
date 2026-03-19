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

    suspend fun encodeSignPublicKey(publicKey: ECDSA.PublicKey): ByteArray =
        publicKey.encodeToByteArray(EC.PublicKey.Format.DER)

    suspend fun decodeSignPublicKey(bytes: ByteArray): ECDSA.PublicKey =
        signPublicKeyDecoder.decodeFromByteArray(
            format = EC.PublicKey.Format.DER,
            bytes = bytes,
        )

    suspend fun encodeSignPrivateKey(privateKey: ECDSA.PrivateKey): ByteArray =
        privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)

    suspend fun decodeSignPrivateKey(bytes: ByteArray): ECDSA.PrivateKey =
        signPrivateKeyDecoder.decodeFromByteArray(
            format = EC.PrivateKey.Format.DER,
            bytes = bytes,
        )

    suspend fun encodeSignKeyPair(keyPair: ECDSA.KeyPair): ByteArray {
        val publicKeyBytes = encodeSignPublicKey(keyPair.publicKey)
        val privateKeyBytes = encodeSignPrivateKey(keyPair.privateKey)

        val totalSize = 4 + publicKeyBytes.size + 4 + privateKeyBytes.size

        return ByteArray(totalSize).apply {
            var offset = 0
            writeIntToByteArray(this, publicKeyBytes.size, offset)
            offset += 4
            publicKeyBytes.copyInto(this, offset)
            offset += publicKeyBytes.size
            writeIntToByteArray(this, privateKeyBytes.size, offset)
            offset += 4
            privateKeyBytes.copyInto(this, offset)
        }
    }

    suspend fun decodeSignKeyPair(bytes: ByteArray): ECDSA.KeyPair {
        var offset = 0
        val (publicKeyBytes, nextOffset1) = readSegment(bytes, offset, "sign public key")
        offset = nextOffset1
        val (privateKeyBytes, _) = readSegment(bytes, offset, "sign private key")

        return ECDSAKeyPairImpl(
            publicKey = decodeSignPublicKey(publicKeyBytes),
            privateKey = decodeSignPrivateKey(privateKeyBytes),
        )
    }

    suspend fun encodeCryptPublicKey(publicKey: ECDH.PublicKey): ByteArray =
        publicKey.encodeToByteArray(EC.PublicKey.Format.DER)

    suspend fun decodeCryptPublicKey(bytes: ByteArray): ECDH.PublicKey =
        cryptPublicKeyDecoder.decodeFromByteArray(
            format = EC.PublicKey.Format.DER,
            bytes = bytes,
        )

    suspend fun encodeCryptPrivateKey(privateKey: ECDH.PrivateKey): ByteArray =
        privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)

    suspend fun decodeCryptPrivateKey(bytes: ByteArray): ECDH.PrivateKey =
        cryptPrivateKeyDecoder.decodeFromByteArray(
            format = EC.PrivateKey.Format.DER,
            bytes = bytes,
        )

    suspend fun encodeCryptKeyPair(keyPair: ECDH.KeyPair): ByteArray {
        val publicKeyBytes = encodeCryptPublicKey(keyPair.publicKey)
        val privateKeyBytes = encodeCryptPrivateKey(keyPair.privateKey)

        val totalSize = 4 + publicKeyBytes.size + 4 + privateKeyBytes.size

        return ByteArray(totalSize).apply {
            var offset = 0
            writeIntToByteArray(this, publicKeyBytes.size, offset)
            offset += 4
            publicKeyBytes.copyInto(this, offset)
            offset += publicKeyBytes.size
            writeIntToByteArray(this, privateKeyBytes.size, offset)
            offset += 4
            privateKeyBytes.copyInto(this, offset)
        }
    }

    suspend fun decodeCryptKeyPair(bytes: ByteArray): ECDH.KeyPair {
        var offset = 0
        val (publicKeyBytes, nextOffset1) = readSegment(bytes, offset, "crypt public key")
        offset = nextOffset1
        val (privateKeyBytes, _) = readSegment(bytes, offset, "crypt private key")

        return ECDHKeyPairImpl(
            publicKey = decodeCryptPublicKey(publicKeyBytes),
            privateKey = decodeCryptPrivateKey(privateKeyBytes),
        )
    }

    suspend fun decodeSecureKeyPair(bytes: ByteArray): SecureKeyPair {
        var offset = 0
        val (signKeyPairBytes, nextOffset1) = readSegment(bytes, offset, "sign key pair")
        offset = nextOffset1
        val (cryptKeyPairBytes, _) = readSegment(bytes, offset, "crypt key pair")

        val signKeyPair = decodeSignKeyPair(signKeyPairBytes)
        val cryptKeyPair = decodeCryptKeyPair(cryptKeyPairBytes)

        return SecureKeyPair(signKeyPair, cryptKeyPair)
    }

    suspend fun encodeSecureKeyPair(keyPair: SecureKeyPair): ByteArray {
        val signKeyPairBytes = encodeSignKeyPair(keyPair.signKeyPair)
        val cryptKeyPairBytes = encodeCryptKeyPair(keyPair.cryptKeyPair)

        val totalSize = 4 + signKeyPairBytes.size + 4 + cryptKeyPairBytes.size

        return ByteArray(totalSize).apply {
            var offset = 0
            writeIntToByteArray(this, signKeyPairBytes.size, offset)
            offset += 4
            signKeyPairBytes.copyInto(this, offset)
            offset += signKeyPairBytes.size
            writeIntToByteArray(this, cryptKeyPairBytes.size, offset)
            offset += 4
            cryptKeyPairBytes.copyInto(this, offset)
        }
    }

    private fun readSegment(
        bytes: ByteArray,
        offset: Int,
        label: String,
    ): Pair<ByteArray, Int> {
        require(bytes.size >= offset + 4) { "Truncated key data: missing $label size at offset $offset" }
        val size = readIntFromByteArray(bytes, offset)
        require(size >= 0 && bytes.size >= offset + 4 + size) {
            "Invalid $label size: $size at offset $offset (available: ${bytes.size - offset - 4})"
        }
        val segment = bytes.sliceArray(offset + 4 until offset + 4 + size)
        return Pair(segment, offset + 4 + size)
    }

    private fun writeIntToByteArray(
        dest: ByteArray,
        value: Int,
        offset: Int,
    ) {
        dest[offset] = (value shr 24).toByte()
        dest[offset + 1] = (value shr 16).toByte()
        dest[offset + 2] = (value shr 8).toByte()
        dest[offset + 3] = value.toByte()
    }

    private fun readIntFromByteArray(
        src: ByteArray,
        offset: Int,
    ): Int =
        ((src[offset].toInt() and 0xFF) shl 24) or
            ((src[offset + 1].toInt() and 0xFF) shl 16) or
            ((src[offset + 2].toInt() and 0xFF) shl 8) or
            (src[offset + 3].toInt() and 0xFF)
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
