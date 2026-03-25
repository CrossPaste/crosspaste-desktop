package com.crosspaste.utils

import com.crosspaste.dto.secure.KeyExchangeRequest
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustConfirmRequest
import com.crosspaste.dto.secure.TrustConfirmResponse
import com.crosspaste.secure.SecureKeyPair
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256

object CryptographyUtils {

    private val provider = CryptographyProvider.Default
    private val sha256 = provider.get(SHA256)

    private val codecsUtils = getCodecsUtils()

    suspend fun generateSecureKeyPair(): SecureKeyPair {
        val ecdsa = provider.get(ECDSA)
        val ecdh = provider.get(ECDH)
        val signKeyPairGenerator = ecdsa.keyPairGenerator(EC.Curve.P256)
        val signKeyPair = signKeyPairGenerator.generateKey()

        val cryptKeyPairGenerator = ecdh.keyPairGenerator(EC.Curve.P256)
        val cryptKeyPair = cryptKeyPairGenerator.generateKey()
        return SecureKeyPair(signKeyPair, cryptKeyPair)
    }

    suspend fun signData(
        privateKey: ECDSA.PrivateKey,
        createSignData: () -> ByteArray,
    ): ByteArray =
        privateKey
            .signatureGenerator(sha256.id, ECDSA.SignatureFormat.DER)
            .generateSignature(createSignData())

    suspend fun verifyData(
        publicKey: ECDSA.PublicKey,
        signature: ByteArray,
        createVerifyData: () -> ByteArray,
    ): Boolean =
        publicKey
            .signatureVerifier(sha256.id, ECDSA.SignatureFormat.DER)
            .tryVerifySignature(createVerifyData(), signature)

    suspend fun signPairingRequest(
        privateKey: ECDSA.PrivateKey,
        pairingRequest: PairingRequest,
    ): ByteArray =
        signData(privateKey) {
            buildString {
                append(codecsUtils.base64Encode(pairingRequest.signPublicKey))
                append(codecsUtils.base64Encode(pairingRequest.cryptPublicKey))
                append(pairingRequest.token)
                append(pairingRequest.timestamp)
            }.encodeToByteArray()
        }

    suspend fun verifyPairingRequest(
        publicKey: ECDSA.PublicKey,
        pairingRequest: PairingRequest,
        signature: ByteArray,
    ): Boolean =
        verifyData(publicKey, signature) {
            buildString {
                append(codecsUtils.base64Encode(pairingRequest.signPublicKey))
                append(codecsUtils.base64Encode(pairingRequest.cryptPublicKey))
                append(pairingRequest.token)
                append(pairingRequest.timestamp)
            }.encodeToByteArray()
        }

    suspend fun signPairingResponse(
        privateKey: ECDSA.PrivateKey,
        pairingResponse: PairingResponse,
    ): ByteArray =
        signData(privateKey) {
            buildString {
                append(codecsUtils.base64Encode(pairingResponse.signPublicKey))
                append(codecsUtils.base64Encode(pairingResponse.cryptPublicKey))
                append(pairingResponse.timestamp)
            }.encodeToByteArray()
        }

    suspend fun verifyPairingResponse(
        publicKey: ECDSA.PublicKey,
        pairingResponse: PairingResponse,
        signature: ByteArray,
    ): Boolean =
        verifyData(publicKey, signature) {
            buildString {
                append(codecsUtils.base64Encode(pairingResponse.signPublicKey))
                append(codecsUtils.base64Encode(pairingResponse.cryptPublicKey))
                append(pairingResponse.timestamp)
            }.encodeToByteArray()
        }

    suspend fun computeSAS(
        pubKeyA: ByteArray,
        pubKeyB: ByteArray,
    ): Int {
        val sorted =
            if (compareLexicographically(pubKeyA, pubKeyB) <= 0) {
                pubKeyA + pubKeyB
            } else {
                pubKeyB + pubKeyA
            }
        val hash = sha256.hasher().hash(sorted)
        val value =
            ((hash[0].toInt() and 0xFF) shl 24) or
                ((hash[1].toInt() and 0xFF) shl 16) or
                ((hash[2].toInt() and 0xFF) shl 8) or
                (hash[3].toInt() and 0xFF)
        return (value and 0x7FFFFFFF) % 1_000_000
    }

    private fun compareLexicographically(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }

    suspend fun signKeyExchangeRequest(
        privateKey: ECDSA.PrivateKey,
        signPublicKey: ByteArray,
        cryptPublicKey: ByteArray,
        timestamp: Long,
    ): ByteArray =
        signData(privateKey) {
            buildString {
                append(codecsUtils.base64Encode(signPublicKey))
                append(codecsUtils.base64Encode(cryptPublicKey))
                append(timestamp)
            }.encodeToByteArray()
        }

    suspend fun verifyKeyExchangeRequest(
        publicKey: ECDSA.PublicKey,
        request: KeyExchangeRequest,
    ): Boolean =
        verifyData(publicKey, request.signature) {
            buildString {
                append(codecsUtils.base64Encode(request.signPublicKey))
                append(codecsUtils.base64Encode(request.cryptPublicKey))
                append(request.timestamp)
            }.encodeToByteArray()
        }

    suspend fun signKeyExchangeResponse(
        privateKey: ECDSA.PrivateKey,
        signPublicKey: ByteArray,
        cryptPublicKey: ByteArray,
        timestamp: Long,
    ): ByteArray =
        signData(privateKey) {
            buildString {
                append(codecsUtils.base64Encode(signPublicKey))
                append(codecsUtils.base64Encode(cryptPublicKey))
                append(timestamp)
            }.encodeToByteArray()
        }

    suspend fun verifyKeyExchangeResponse(
        publicKey: ECDSA.PublicKey,
        response: KeyExchangeResponse,
    ): Boolean =
        verifyData(publicKey, response.signature) {
            buildString {
                append(codecsUtils.base64Encode(response.signPublicKey))
                append(codecsUtils.base64Encode(response.cryptPublicKey))
                append(response.timestamp)
            }.encodeToByteArray()
        }

    suspend fun signTrustConfirm(
        privateKey: ECDSA.PrivateKey,
        timestamp: Long,
    ): ByteArray =
        signData(privateKey) {
            timestamp.toString().encodeToByteArray()
        }

    suspend fun verifyTrustConfirm(
        publicKey: ECDSA.PublicKey,
        request: TrustConfirmRequest,
    ): Boolean =
        verifyData(publicKey, request.signature) {
            request.timestamp.toString().encodeToByteArray()
        }

    suspend fun verifyTrustConfirmResponse(
        publicKey: ECDSA.PublicKey,
        response: TrustConfirmResponse,
    ): Boolean =
        verifyData(publicKey, response.signature) {
            response.timestamp.toString().encodeToByteArray()
        }
}
