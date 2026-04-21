@file:OptIn(ExperimentalJsExport::class)

package com.crosspaste.web

import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.paste.PasteData
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getJsonUtils
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

private val jsonUtils = getJsonUtils()
private val codecsUtils = getCodecsUtils()
private val provider = CryptographyProvider.Default

/**
 * JSON parsing facade — exposed to TypeScript.
 */
@JsExport
object CrossPasteJson {

    /** Parse a PasteData JSON string, returns re-serialized JSON. */
    fun parsePasteData(jsonString: String): String {
        val pasteData = jsonUtils.JSON.decodeFromString<PasteData>(jsonString)
        return jsonUtils.JSON.encodeToString(pasteData)
    }

    /** Parse a SyncInfo JSON string, returns re-serialized JSON. */
    fun parseSyncInfo(jsonString: String): String {
        val syncInfo = jsonUtils.JSON.decodeFromString<SyncInfo>(jsonString)
        return jsonUtils.JSON.encodeToString(syncInfo)
    }

    /** Encode a TrustRequest to JSON string. */
    fun encodeTrustRequest(trustRequest: String): String = trustRequest

    /** Decode a TrustResponse from JSON string. */
    fun decodeTrustResponse(jsonString: String): String {
        val response = jsonUtils.JSON.decodeFromString<TrustResponse>(jsonString)
        return jsonUtils.JSON.encodeToString(response)
    }
}

/**
 * Hashing facade — exposed to TypeScript.
 */
@JsExport
object CrossPasteHash {

    /** MurmurHash3 128x64 hash of a text string, returns 32-char hex. */
    fun hashText(text: String): String = codecsUtils.hashByString(text)

    /** MurmurHash3 128x64 hash of raw bytes, returns 32-char hex. */
    fun hashBytes(bytes: ByteArray): String = codecsUtils.hash(bytes)

    /** Base64 encode bytes. */
    fun base64Encode(bytes: ByteArray): String = codecsUtils.base64Encode(bytes)

    /** Base64 decode string to bytes. */
    fun base64Decode(str: String): ByteArray = codecsUtils.base64Decode(str)
}

/**
 * Key pair data returned to TypeScript.
 */
@JsExport
data class JsKeyPair(
    val signPublicKey: ByteArray,
    val signPrivateKey: ByteArray,
    val cryptPublicKey: ByteArray,
    val cryptPrivateKey: ByteArray,
)

/**
 * Crypto facade — exposed to TypeScript.
 * All methods return Promise because WebCrypto is async.
 */
@JsExport
object CrossPasteCrypto {

    /** Generate ECDSA P-256 sign keypair + ECDH P-256 crypt keypair. */
    @Suppress("OPT_IN_USAGE")
    fun generateKeyPair(): Promise<JsKeyPair> =
        GlobalScope.promise {
            val serializer = SecureKeyPairSerializer()
            val keyPair = CryptographyUtils.generateSecureKeyPair()
            JsKeyPair(
                signPublicKey = serializer.encodeSignPublicKey(keyPair.signKeyPair.publicKey),
                signPrivateKey = serializer.encodeSignPrivateKey(keyPair.signKeyPair.privateKey),
                cryptPublicKey = serializer.encodeCryptPublicKey(keyPair.cryptKeyPair.publicKey),
                cryptPrivateKey = serializer.encodeCryptPrivateKey(keyPair.cryptKeyPair.privateKey),
            )
        }

    /**
     * Build a signed TrustRequest JSON.
     * @param signPrivateKeyDer DER-encoded ECDSA private key
     * @param signPublicKeyDer DER-encoded ECDSA public key
     * @param cryptPublicKeyDer DER-encoded ECDH public key
     * @param token 6-digit pairing token
     * @return TrustRequest as JSON string
     */
    @Suppress("OPT_IN_USAGE")
    fun buildTrustRequest(
        signPrivateKeyDer: ByteArray,
        signPublicKeyDer: ByteArray,
        cryptPublicKeyDer: ByteArray,
        token: Int,
    ): Promise<String> =
        GlobalScope.promise {
            val serializer = SecureKeyPairSerializer()
            val privateKey = serializer.decodeSignPrivateKey(signPrivateKeyDer)

            val timestamp =
                kotlin.time.Clock.System
                    .now()
                    .toEpochMilliseconds()

            val pairingRequest =
                PairingRequest(
                    signPublicKey = signPublicKeyDer,
                    cryptPublicKey = cryptPublicKeyDer,
                    token = token,
                    timestamp = timestamp,
                )

            val signature = CryptographyUtils.signPairingRequest(privateKey, pairingRequest)

            val trustRequest = TrustRequest(pairingRequest, signature)
            jsonUtils.JSON.encodeToString(trustRequest)
        }

    /**
     * Verify a TrustResponse and extract the server's public keys.
     * @param signPublicKeyDer server's DER-encoded ECDSA sign public key (from response)
     * @param responseJson TrustResponse JSON
     * @return true if signature is valid
     */
    @Suppress("OPT_IN_USAGE")
    fun verifyTrustResponse(
        signPublicKeyDer: ByteArray,
        responseJson: String,
    ): Promise<Boolean> =
        GlobalScope.promise {
            val serializer = SecureKeyPairSerializer()
            val publicKey = serializer.decodeSignPublicKey(signPublicKeyDer)
            val response = jsonUtils.JSON.decodeFromString<TrustResponse>(responseJson)

            CryptographyUtils.verifyPairingResponse(
                publicKey,
                response.pairingResponse,
                response.signature,
            )
        }

    /**
     * Decode a DER-encoded ECDSA public key and re-export for verification.
     */
    @Suppress("OPT_IN_USAGE")
    fun decodeSignPublicKey(derBytes: ByteArray): Promise<ByteArray> =
        GlobalScope.promise {
            val serializer = SecureKeyPairSerializer()
            val pubKey = serializer.decodeSignPublicKey(derBytes)
            serializer.encodeSignPublicKey(pubKey)
        }
}
