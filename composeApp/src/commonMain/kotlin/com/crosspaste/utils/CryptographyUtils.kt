package com.crosspaste.utils

import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.PairingResponse
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256

object CryptographyUtils {

    private val provider = CryptographyProvider.Default
    private val sha256 = provider.get(SHA256)

    private val codecsUtils = getCodecsUtils()

    fun signPairingRequest(
        privateKey: ECDSA.PrivateKey,
        pairingRequest: PairingRequest,
    ): ByteArray {
        val dataToSign =
            buildString {
                append(codecsUtils.base64Encode(pairingRequest.signPublicKey))
                append(codecsUtils.base64Encode(pairingRequest.cryptPublicKey))
                append(pairingRequest.token)
                append(pairingRequest.timestamp)
            }.toByteArray()

        return privateKey.signatureGenerator(sha256.id, ECDSA.SignatureFormat.DER)
            .generateSignatureBlocking(dataToSign)
    }

    fun verifyPairingRequest(
        publicKey: ECDSA.PublicKey,
        pairingRequest: PairingRequest,
        signature: ByteArray,
    ): Boolean {
        val dataToVerify =
            buildString {
                append(codecsUtils.base64Encode(pairingRequest.signPublicKey))
                append(codecsUtils.base64Encode(pairingRequest.cryptPublicKey))
                append(pairingRequest.token)
                append(pairingRequest.timestamp)
            }.toByteArray()

        return publicKey.signatureVerifier(sha256.id, ECDSA.SignatureFormat.DER)
            .tryVerifySignatureBlocking(dataToVerify, signature)
    }

    fun signPairingResponse(
        privateKey: ECDSA.PrivateKey,
        pairingResponse: PairingResponse,
    ): ByteArray {
        val dataToSign =
            buildString {
                append(codecsUtils.base64Encode(pairingResponse.signPublicKey))
                append(codecsUtils.base64Encode(pairingResponse.cryptPublicKey))
                append(pairingResponse.timestamp)
            }.toByteArray()

        return privateKey.signatureGenerator(sha256.id, ECDSA.SignatureFormat.DER)
            .generateSignatureBlocking(dataToSign)
    }

    fun verifyPairingResponse(
        publicKey: ECDSA.PublicKey,
        pairingResponse: PairingResponse,
        signature: ByteArray,
    ): Boolean {
        val dataToVerify =
            buildString {
                append(codecsUtils.base64Encode(pairingResponse.signPublicKey))
                append(codecsUtils.base64Encode(pairingResponse.cryptPublicKey))
                append(pairingResponse.timestamp)
            }.toByteArray()

        return publicKey.signatureVerifier(sha256.id, ECDSA.SignatureFormat.DER)
            .tryVerifySignatureBlocking(dataToVerify, signature)
    }
}
