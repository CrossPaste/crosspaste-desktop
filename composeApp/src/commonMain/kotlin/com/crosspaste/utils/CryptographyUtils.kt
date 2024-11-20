package com.crosspaste.utils

import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.secure.SecureKeyPair
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.utils.io.core.toByteArray

object CryptographyUtils {

    private val provider = CryptographyProvider.Default
    private val sha256 = provider.get(SHA256)

    private val codecsUtils = getCodecsUtils()

    fun generateSecureKeyPair(): SecureKeyPair {
        val provider = CryptographyProvider.Default
        val ecdsa = provider.get(ECDSA)
        val ecdh = provider.get(ECDH)
        val signKeyPairGenerator = ecdsa.keyPairGenerator(EC.Curve.P256)
        val signKeyPair = signKeyPairGenerator.generateKeyBlocking()

        val cryptKeyPairGenerator = ecdh.keyPairGenerator(EC.Curve.P256)
        val cryptKeyPair = cryptKeyPairGenerator.generateKeyBlocking()
        return SecureKeyPair(signKeyPair, cryptKeyPair)
    }

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
