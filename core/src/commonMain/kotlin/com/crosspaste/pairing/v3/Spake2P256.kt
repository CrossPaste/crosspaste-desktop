package com.crosspaste.pairing.v3

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Frozen constants and the protocol composition for SPAKE2 over P-256, RFC 9382
 * (ADR D1–D7). This layer performs NO elliptic-curve arithmetic; it decodes the
 * fixed M/N constants, derives `w` from the PIN, drives the SPAKE2 message flow,
 * assembles the RFC transcript TT, and extracts Ke — all group operations are
 * delegated to a [PakeEcOps] backend.
 */
object Spake2P256 {

    /**
     * RFC 9382 §6 constant M for P-256, compressed SEC1. Decoded to a curve point
     * once per session by the backend. Frozen wire constant.
     */
    const val M_COMPRESSED_HEX: String =
        "02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f"

    /** RFC 9382 §6 constant N for P-256, compressed SEC1. Frozen wire constant. */
    const val N_COMPRESSED_HEX: String =
        "03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49"

    /** HKDF `info` for the PIN-to-`w` derivation (ADR D4). Frozen. */
    const val W_INFO: String = "CrossPaste-v3-SPAKE2-w"

    /** Wide HKDF output length before reduction mod n (ADR D4): 48 bytes. */
    const val W_WIDE_LENGTH: Int = 48

    /** SPAKE2 Ke length for the SHA-256 suite: first half of Hash(TT), 16 bytes (RFC 9382 §4). */
    const val KE_LENGTH: Int = 16

    internal fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd-length hex" }
        return ByteArray(hex.length / 2) { index ->
            val hi = hex[index * 2].digitToInt(16)
            val lo = hex[index * 2 + 1].digitToInt(16)
            ((hi shl 4) or lo).toByte()
        }
    }
}

/**
 * SPAKE2/P-256 [PakeProvider], RFC 9382 (ADR D2 ciphersuite). Backed by a
 * platform [PakeEcOps]; the same protocol code runs on every platform and only
 * the EC backend differs. See [PakeProvider] for the frozen role/output contract.
 */
class Spake2PakeProvider(
    private val ecOps: PakeEcOps,
) : PakeProvider {

    override val ciphersuite: String = PairingV3.CIPHERSUITE_SPAKE2_P256

    private val sha256 = CryptographyProvider.Default.get(SHA256)

    // The frozen M/N constants normalized to uncompressed form once per provider.
    private val mPoint: ByteArray by lazy { ecOps.toUncompressed(Spake2P256.hexToBytes(Spake2P256.M_COMPRESSED_HEX)) }
    private val nPoint: ByteArray by lazy { ecOps.toUncompressed(Spake2P256.hexToBytes(Spake2P256.N_COMPRESSED_HEX)) }

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession {
        val w = deriveW(pin, context.pinContext)
        val privateScalar = ecOps.randomScalar()
        // pA = w*M + x*G (initiator) or pB = w*N + y*G (acceptor).
        val ownConstant = if (role == PakeRole.INITIATOR) mPoint else nPoint
        val localShare =
            try {
                ecOps.addPoints(ecOps.mulPoint(ownConstant, w), ecOps.mulBase(privateScalar))
            } catch (e: Throwable) {
                // Any backend failure must not leave secret scalars in cleared-nowhere
                // buffers; zero both before propagating, whatever the error type.
                w.fill(0)
                privateScalar.fill(0)
                throw e
            }
        return Spake2Session(
            role = role,
            ecOps = ecOps,
            hash = { data -> sha256.hasher().hash(data) },
            w = w,
            privateScalar = privateScalar,
            localShare = localShare,
            peerConstant = if (role == PakeRole.INITIATOR) nPoint else mPoint,
        )
    }

    /**
     * ADR D4: `w = OS2IP(HKDF-SHA256(salt = SHA-256(pinContext), IKM = UTF-8(pin),
     * info = "CrossPaste-v3-SPAKE2-w", L = 48)) mod n`. No memory-hard function —
     * the PIN is a one-time 30-second secret and SPAKE2 rules out offline grinding.
     */
    private suspend fun deriveW(
        pin: CharArray,
        pinContext: ByteArray,
    ): ByteArray {
        // Encode the PIN straight from the CharArray: the digits are ASCII, so no
        // intermediate immutable String (which could not be zeroed) is created.
        val pinBytes = ByteArray(pin.size) { index -> pin[index].code.toByte() }
        val salt = sha256.hasher().hash(pinContext)
        val prk = PairingKeySchedule.hkdfExtract(salt = salt, ikm = pinBytes)
        val wide = PairingKeySchedule.hkdfExpand(prk, Spake2P256.W_INFO.encodeToByteArray(), Spake2P256.W_WIDE_LENGTH)
        prk.fill(0)
        pinBytes.fill(0)
        val w =
            try {
                ecOps.reduceScalar(wide)
            } finally {
                wide.fill(0)
            }
        // w == 0 would drop the M/N binding (0*M + x*G = x*G). It requires the HKDF
        // output to be a multiple of the group order (probability ~2^-256), but the
        // interface contract promises a hard failure, so enforce it.
        if (w.all { byte -> byte.toInt() == 0 }) {
            w.fill(0)
            throw PakeException("degenerate SPAKE2 w")
        }
        return w
    }
}

private class Spake2Session(
    private val role: PakeRole,
    private val ecOps: PakeEcOps,
    private val hash: suspend (ByteArray) -> ByteArray,
    private val w: ByteArray,
    private val privateScalar: ByteArray,
    private val localShare: ByteArray,
    /** N for the initiator, M for the acceptor — the constant to strip from the peer share. */
    private val peerConstant: ByteArray,
) : PakeSession {

    private var destroyed = false

    override suspend fun localShare(): ByteArray {
        check(!destroyed) { "session destroyed" }
        return localShare.copyOf()
    }

    override suspend fun deriveSharedSecret(peerShare: ByteArray): ByteArray {
        check(!destroyed) { "session destroyed" }
        // K = h * privateScalar * (peerShare - w * peerConstant); h = 1 for P-256.
        // ecOps validates peerShare lies on the curve and is not the identity.
        val stripped = ecOps.subtractPoints(peerShare, ecOps.mulPoint(peerConstant, w))
        val k = ecOps.mulPoint(stripped, privateScalar)

        // RFC 9382 §3.3 transcript with implicit (empty) identities: identity
        // binding is carried by w (derived from the full pinContext) and by the v3
        // transcript MACs (ADR D6). pA is always the initiator share, pB the
        // acceptor share, regardless of which side computes.
        val (pa, pb) =
            when (role) {
                PakeRole.INITIATOR -> localShare to peerShare
                PakeRole.ACCEPTOR -> peerShare to localShare
            }
        val tt =
            buildTranscript(
                a = ByteArray(0),
                b = ByteArray(0),
                pa = pa,
                pb = pb,
                k = k,
                w = w,
            )
        val digest = hash(tt)
        k.fill(0)
        tt.fill(0)
        // Ke is the first half of Hash(TT) (RFC 9382 §4); Ka is discarded.
        val ke = digest.copyOf(Spake2P256.KE_LENGTH)
        digest.fill(0)
        return ke
    }

    override fun destroy() {
        destroyed = true
        w.fill(0)
        privateScalar.fill(0)
    }

    /**
     * `TT = len(A)||A || len(B)||B || len(pA)||pA || len(pB)||pB || len(K)||K || len(w)||w`.
     *
     * Assembled into a single pre-sized [ByteArray] with no per-byte boxing, so the
     * secret-bearing K and w never leak into un-zeroable intermediate objects; the
     * caller zeroes the returned buffer.
     */
    private fun buildTranscript(
        a: ByteArray,
        b: ByteArray,
        pa: ByteArray,
        pb: ByteArray,
        k: ByteArray,
        w: ByteArray,
    ): ByteArray {
        val fields = arrayOf(a, b, pa, pb, k, w)
        val total = fields.sumOf { field -> LENGTH_PREFIX_SIZE + field.size }
        val out = ByteArray(total)
        var offset = 0
        for (field in fields) {
            writeLe64(out, offset, field.size)
            offset += LENGTH_PREFIX_SIZE
            field.copyInto(out, offset)
            offset += field.size
        }
        return out
    }

    /** Writes an eight-byte little-endian length prefix in place (RFC 9382 §3.3). */
    private fun writeLe64(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        val v = value.toLong()
        for (index in 0 until LENGTH_PREFIX_SIZE) {
            target[offset + index] = ((v ushr (index * 8)) and 0xFF).toByte()
        }
    }

    private companion object {
        const val LENGTH_PREFIX_SIZE = 8
    }
}
