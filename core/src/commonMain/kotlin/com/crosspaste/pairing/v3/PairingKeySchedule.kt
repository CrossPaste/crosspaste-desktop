package com.crosspaste.pairing.v3

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Independent keys derived from one PAKE output, RFC 5869 HKDF-SHA256 with
 * the transcript hash as salt. No key is ever reused for two purposes.
 */
class PairingSessionKeys(
    val confirmInitiator: ByteArray,
    val confirmAcceptor: ByteArray,
    val handshakeAead: ByteArray,
    val receipt: ByteArray,
) {
    fun clear() {
        confirmInitiator.fill(0)
        confirmAcceptor.fill(0)
        handshakeAead.fill(0)
        receipt.fill(0)
    }
}

object PairingKeySchedule {

    private const val HASH_SIZE = 32

    private val hmac = CryptographyProvider.Default.get(HMAC)

    suspend fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray =
        hmac
            .keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
            .signatureGenerator()
            .generateSignature(data)

    internal suspend fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hmacSha256(if (salt.isEmpty()) ByteArray(HASH_SIZE) else salt, ikm)

    internal suspend fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        require(length in 1..255 * HASH_SIZE) { "invalid HKDF output length: $length" }
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var blockIndex = 1
        while (offset < length) {
            previous = hmacSha256(prk, previous + info + byteArrayOf(blockIndex.toByte()))
            previous.copyInto(output, offset, 0, minOf(previous.size, length - offset))
            offset += previous.size
            blockIndex++
        }
        return output
    }

    suspend fun deriveSessionKeys(
        transcriptHash: ByteArray,
        pakeSharedSecret: ByteArray,
    ): PairingSessionKeys {
        val prk = hkdfExtract(salt = transcriptHash, ikm = pakeSharedSecret)
        val keys =
            PairingSessionKeys(
                confirmInitiator = expandLabel(prk, PairingV3.LABEL_CONFIRM_INITIATOR),
                confirmAcceptor = expandLabel(prk, PairingV3.LABEL_CONFIRM_ACCEPTOR),
                handshakeAead = expandLabel(prk, PairingV3.LABEL_HANDSHAKE_AEAD),
                receipt = expandLabel(prk, PairingV3.LABEL_COMMIT_RECEIPT),
            )
        prk.fill(0)
        return keys
    }

    private suspend fun expandLabel(
        prk: ByteArray,
        label: String,
    ): ByteArray = hkdfExpand(prk, label.encodeToByteArray(), PairingV3.DERIVED_KEY_SIZE)

    suspend fun initiatorConfirmation(
        keys: PairingSessionKeys,
        transcriptHash: ByteArray,
    ): ByteArray =
        hmacSha256(
            keys.confirmInitiator,
            transcriptHash + PairingV3.CONTEXT_INITIATOR_CONFIRM.encodeToByteArray(),
        )

    suspend fun acceptorConfirmation(
        keys: PairingSessionKeys,
        transcriptHash: ByteArray,
    ): ByteArray =
        hmacSha256(
            keys.confirmAcceptor,
            transcriptHash + PairingV3.CONTEXT_ACCEPTOR_CONFIRM.encodeToByteArray(),
        )

    suspend fun commitMac(
        keys: PairingSessionKeys,
        transcriptHash: ByteArray,
    ): ByteArray = hmacSha256(keys.receipt, transcriptHash + PairingV3.CONTEXT_INITIATOR_COMMIT.encodeToByteArray())

    suspend fun receiptMac(
        keys: PairingSessionKeys,
        transcriptHash: ByteArray,
    ): ByteArray = hmacSha256(keys.receipt, transcriptHash + PairingV3.CONTEXT_ACCEPTOR_COMMIT_ACK.encodeToByteArray())

    /** Payload signed by a peer's long-term identity signing key. */
    fun identitySignaturePayload(
        role: PakeRole,
        transcriptHash: ByteArray,
    ): ByteArray =
        when (role) {
            PakeRole.INITIATOR -> transcriptHash + PairingV3.CONTEXT_INITIATOR_IDENTITY.encodeToByteArray()
            PakeRole.ACCEPTOR -> transcriptHash + PairingV3.CONTEXT_ACCEPTOR_IDENTITY.encodeToByteArray()
        }

    /** Constant-time comparison for MACs and confirmation values. */
    fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray,
    ): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
