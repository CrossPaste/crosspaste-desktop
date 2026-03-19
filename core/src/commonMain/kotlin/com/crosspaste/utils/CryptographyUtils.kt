package com.crosspaste.utils

import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.PairingResponse
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
}
