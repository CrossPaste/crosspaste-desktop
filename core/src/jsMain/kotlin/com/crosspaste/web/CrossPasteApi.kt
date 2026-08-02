@file:OptIn(ExperimentalJsExport::class)

package com.crosspaste.web

import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.secure.KeyExchangeRequest
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.TrustConfirmRequest
import com.crosspaste.dto.secure.TrustConfirmResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.pairing.v3.NoblePakeEcOps
import com.crosspaste.pairing.v3.PairingV3InitiatorEngine
import com.crosspaste.pairing.v3.Spake2PakeProvider
import com.crosspaste.paste.PasteData
import com.crosspaste.secure.ECDHKeyPairImpl
import com.crosspaste.secure.ECDSAKeyPairImpl
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureMessageCipher
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getJsonUtils
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
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

    private val lastV2ExchangeGeneration = atomic(0L)

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

    // region Pairing v2 (SAS / ECDH)

    /**
     * Build a signed KeyExchangeRequest JSON for `POST /sync/trust/v2/exchange`.
     */
    @Suppress("OPT_IN_USAGE")
    fun buildKeyExchangeRequest(
        signPrivateKeyDer: ByteArray,
        signPublicKeyDer: ByteArray,
        cryptPublicKeyDer: ByteArray,
    ): Promise<String> =
        GlobalScope.promise {
            val serializer = SecureKeyPairSerializer()
            val privateKey = serializer.decodeSignPrivateKey(signPrivateKeyDer)
            val timestamp =
                lastV2ExchangeGeneration.updateAndGet { previous ->
                    maxOf(
                        kotlin.time.Clock.System
                            .now()
                            .toEpochMilliseconds(),
                        previous + 1,
                    )
                }
            val request =
                KeyExchangeRequest(
                    signPublicKey = signPublicKeyDer,
                    cryptPublicKey = cryptPublicKeyDer,
                    timestamp = timestamp,
                    signature =
                        CryptographyUtils.signKeyExchangeRequest(
                            privateKey,
                            signPublicKeyDer,
                            cryptPublicKeyDer,
                            timestamp,
                        ),
                )
            jsonUtils.JSON.encodeToString(request)
        }

    /**
     * Verify a KeyExchangeResponse's self-signature (the response carries the
     * responder's sign public key; trust comes from the SAS comparison, not
     * from this signature alone).
     */
    @Suppress("OPT_IN_USAGE")
    fun verifyKeyExchangeResponse(responseJson: String): Promise<Boolean> =
        GlobalScope.promise {
            runCatching {
                val serializer = SecureKeyPairSerializer()
                val response = jsonUtils.JSON.decodeFromString<KeyExchangeResponse>(responseJson)
                val publicKey = serializer.decodeSignPublicKey(response.signPublicKey)
                // The crypt key must decode as a valid P-256 ECDH key before use.
                serializer.decodeCryptPublicKey(response.cryptPublicKey)
                CryptographyUtils.verifyKeyExchangeResponse(publicKey, response)
            }.getOrDefault(false)
        }

    /**
     * Order-independent 6-digit SAS over the two DER ECDH public keys; must
     * match the code displayed on the desktop after the exchange.
     */
    @Suppress("OPT_IN_USAGE")
    fun computeSAS(
        cryptPublicKeyA: ByteArray,
        cryptPublicKeyB: ByteArray,
    ): Promise<Int> =
        GlobalScope.promise {
            CryptographyUtils.computeSAS(cryptPublicKeyA, cryptPublicKeyB)
        }

    /** Build a signed TrustConfirmRequest JSON for `POST /sync/trust/v2/confirm`. */
    @Suppress("OPT_IN_USAGE")
    fun buildTrustConfirmRequest(signPrivateKeyDer: ByteArray): Promise<String> =
        GlobalScope.promise {
            val serializer = SecureKeyPairSerializer()
            val privateKey = serializer.decodeSignPrivateKey(signPrivateKeyDer)
            val timestamp =
                kotlin.time.Clock.System
                    .now()
                    .toEpochMilliseconds()
            val request =
                TrustConfirmRequest(
                    timestamp = timestamp,
                    signature = CryptographyUtils.signTrustConfirm(privateKey, timestamp),
                )
            jsonUtils.JSON.encodeToString(request)
        }

    /** Verify a TrustConfirmResponse against the responder's sign public key. */
    @Suppress("OPT_IN_USAGE")
    fun verifyTrustConfirmResponse(
        signPublicKeyDer: ByteArray,
        responseJson: String,
    ): Promise<Boolean> =
        GlobalScope.promise {
            runCatching {
                val serializer = SecureKeyPairSerializer()
                val publicKey = serializer.decodeSignPublicKey(signPublicKeyDer)
                val response = jsonUtils.JSON.decodeFromString<TrustConfirmResponse>(responseJson)
                CryptographyUtils.verifyTrustConfirmResponse(publicKey, response)
            }.getOrDefault(false)
        }

    // endregion
}

/**
 * Symmetric message cipher bound to one peer — exposed to TypeScript.
 *
 * Byte-compatible with desktop's `SecureMessageProcessor`: used to decrypt
 * WS envelopes flagged `encrypted` (e.g. PASTE_PUSH when the desktop has
 * "Encrypted Sync" enabled). Construct via [createSecureMessageProcessor].
 */
@JsExport
class JsSecureMessageProcessor internal constructor(
    private val cipher: SecureMessageCipher,
) {

    @Suppress("OPT_IN_USAGE")
    fun encrypt(data: ByteArray): Promise<ByteArray> =
        GlobalScope.promise {
            cipher.encrypt(data)
        }

    @Suppress("OPT_IN_USAGE")
    fun decrypt(data: ByteArray): Promise<ByteArray> =
        GlobalScope.promise {
            cipher.decrypt(data)
        }
}

/**
 * Create a message cipher from our DER ECDH private key and the peer's DER
 * ECDH public key (`serverKeys.cryptPublicKey` persisted at pairing time).
 */
@Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")
@JsExport
fun createSecureMessageProcessor(
    cryptPrivateKeyDer: ByteArray,
    peerCryptPublicKeyDer: ByteArray,
): Promise<JsSecureMessageProcessor> =
    GlobalScope.promise {
        val serializer = SecureKeyPairSerializer()
        JsSecureMessageProcessor(
            SecureMessageCipher(
                privateKey = serializer.decodeCryptPrivateKey(cryptPrivateKeyDer),
                publicKey = serializer.decodeCryptPublicKey(peerCryptPublicKeyDer),
            ),
        )
    }

/**
 * Pairing v3 (SPAKE2 + PIN) initiator session — exposed to TypeScript.
 *
 * One instance drives one pairing attempt. All messages cross as JSON strings
 * of the frozen v3 wire DTOs; the TypeScript side only moves them over HTTP.
 * Construct via [createPairingV3Initiator].
 */
@JsExport
class JsPairingV3Initiator internal constructor(
    private val engine: PairingV3InitiatorEngine,
) {

    /** Intent JSON for `POST /sync/pairing/v3/intent`. Callable once. */
    @Suppress("OPT_IN_USAGE")
    fun createIntent(
        targetAppInstanceId: String,
        displayName: String,
    ): Promise<String> =
        GlobalScope.promise {
            jsonUtils.JSON.encodeToString(engine.createIntent(targetAppInstanceId, displayName))
        }

    /**
     * Byte-identical intent JSON for PIN-rotation refresh (re-POST to
     * `/sync/pairing/v3/intent`, then feed the fresh offer to [acceptOffer]).
     */
    fun intentJson(): String = jsonUtils.JSON.encodeToString(engine.intent)

    /**
     * Validate the offer returned by the intent POST. Resolves to null when the
     * offer is accepted (the desktop is now showing the PIN), or a
     * PairingV3ErrorCode name / "MALFORMED" otherwise.
     */
    @Suppress("OPT_IN_USAGE")
    fun acceptOffer(offerJson: String): Promise<String?> =
        GlobalScope.promise {
            val offer =
                runCatching { jsonUtils.JSON.decodeFromString<PairingOfferV3>(offerJson) }
                    .getOrNull() ?: return@promise "MALFORMED"
            engine.acceptOffer(offer)?.name
        }

    /**
     * Run SPAKE2 with the user-entered 6-digit PIN; resolves to the proof JSON
     * for `POST /sync/pairing/v3/proof`. Rejects on malformed input.
     */
    @Suppress("OPT_IN_USAGE")
    fun buildProof(pin: String): Promise<String> =
        GlobalScope.promise {
            val pinChars = pin.toCharArray()
            try {
                jsonUtils.JSON.encodeToString(engine.buildProof(pinChars))
            } finally {
                pinChars.fill('0')
            }
        }

    /**
     * Verify the acceptor's proof response. False is a hard failure (possible
     * MITM or wrong acceptor state); the session cannot continue.
     */
    @Suppress("OPT_IN_USAGE")
    fun verifyProofResponse(responseJson: String): Promise<Boolean> =
        GlobalScope.promise {
            val response =
                runCatching { jsonUtils.JSON.decodeFromString<PairingProofResponseV3>(responseJson) }
                    .getOrNull() ?: return@promise false
            engine.verifyProofResponse(response)
        }

    /** Commit JSON for `POST /sync/pairing/v3/commit`. Deterministic; safe to retry. */
    @Suppress("OPT_IN_USAGE")
    fun buildCommit(): Promise<String> =
        GlobalScope.promise {
            jsonUtils.JSON.encodeToString(engine.buildCommit())
        }

    /**
     * Verify the acceptor's commit receipt. True completes the pairing; persist
     * [acceptorCryptPublicKey] and [acceptorSignPublicKey] as the peer's trusted keys.
     */
    @Suppress("OPT_IN_USAGE")
    fun verifyCommitAck(ackJson: String): Promise<Boolean> =
        GlobalScope.promise {
            val ack =
                runCatching { jsonUtils.JSON.decodeFromString<PairingCommitAckV3>(ackJson) }
                    .getOrNull() ?: return@promise false
            engine.verifyCommitAck(ack)
        }

    /** Cancel JSON for `POST /sync/pairing/v3/cancel`, or null before a session exists. */
    fun buildCancel(): String? = engine.buildCancel()?.let { jsonUtils.JSON.encodeToString(it) }

    fun acceptorCryptPublicKey(): ByteArray = engine.acceptorCryptPublicKey

    fun acceptorSignPublicKey(): ByteArray = engine.acceptorSignPublicKey

    /** Short acceptor key fingerprint for UI display only. */
    @Suppress("OPT_IN_USAGE")
    fun acceptorKeyFingerprintDisplay(): Promise<String> =
        GlobalScope.promise {
            engine.acceptorKeyFingerprintDisplay()
        }

    /** PIN expiry (epoch millis, ACCEPTOR's clock) — countdown display hint only. */
    fun pinExpiresAt(): Double = engine.pinExpiresAt.toDouble()

    fun tokenGeneration(): Double = engine.tokenGeneration.toDouble()

    /** Clears key material; the session is unusable afterwards. */
    fun destroy() {
        engine.destroy()
    }
}

/**
 * Create a pairing v3 initiator session from the extension's stored DER keys.
 */
@Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")
@JsExport
fun createPairingV3Initiator(
    appInstanceId: String,
    signPublicKeyDer: ByteArray,
    signPrivateKeyDer: ByteArray,
    cryptPublicKeyDer: ByteArray,
    cryptPrivateKeyDer: ByteArray,
): Promise<JsPairingV3Initiator> =
    GlobalScope.promise {
        val serializer = SecureKeyPairSerializer()
        val secureKeyPair =
            SecureKeyPair(
                signKeyPair =
                    ECDSAKeyPairImpl(
                        publicKey = serializer.decodeSignPublicKey(signPublicKeyDer),
                        privateKey = serializer.decodeSignPrivateKey(signPrivateKeyDer),
                    ),
                cryptKeyPair =
                    ECDHKeyPairImpl(
                        publicKey = serializer.decodeCryptPublicKey(cryptPublicKeyDer),
                        privateKey = serializer.decodeCryptPrivateKey(cryptPrivateKeyDer),
                    ),
            )
        JsPairingV3Initiator(
            PairingV3InitiatorEngine(
                appInstanceId = appInstanceId,
                secureKeyPair = secureKeyPair,
                pakeProvider = Spake2PakeProvider(NoblePakeEcOps()),
                secureKeyPairSerializer = serializer,
            ),
        )
    }
