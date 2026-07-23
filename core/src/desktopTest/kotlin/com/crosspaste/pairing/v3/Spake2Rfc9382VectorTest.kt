package com.crosspaste.pairing.v3

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 9382 Appendix B official test vectors for SPAKE2 over P-256, exercised
 * through [BouncyCastlePakeEcOps]. Given the published `w`, `x`, `y` and
 * identities, the backend must reproduce `pA`, `pB`, the shared element `K`, and
 * the derived `Ke` byte-for-byte. This is the correctness gate for the EC backend
 * and the transcript/Ke composition; a mismatch fails here.
 *
 * The mandatory `pinContext` binding, `w` derivation from the PIN, and end-to-end
 * agreement are covered by [Spake2PakeProviderTest]; this file pins the raw math
 * to the RFC.
 */
class Spake2Rfc9382VectorTest {

    private val ec = BouncyCastlePakeEcOps()

    private fun hex(s: String): ByteArray = Spake2P256.hexToBytes(s)

    private fun ByteArray.hex(): String = joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private val m = ec.toUncompressed(hex(Spake2P256.M_COMPRESSED_HEX))
    private val n = ec.toUncompressed(hex(Spake2P256.N_COMPRESSED_HEX))

    /** pA = w*M + x*G ; pB = w*N + y*G. */
    private fun shares(
        w: ByteArray,
        x: ByteArray,
        y: ByteArray,
    ): Pair<ByteArray, ByteArray> {
        val pa = ec.addPoints(ec.mulPoint(m, w), ec.mulBase(x))
        val pb = ec.addPoints(ec.mulPoint(n, w), ec.mulBase(y))
        return pa to pb
    }

    /** A computes K = x*(pB - w*N). */
    private fun sharedK(
        w: ByteArray,
        x: ByteArray,
        pb: ByteArray,
    ): ByteArray = ec.mulPoint(ec.subtractPoints(pb, ec.mulPoint(n, w)), x)

    private fun le64(value: Int): ByteArray = ByteArray(8) { i -> ((value.toLong() ushr (i * 8)) and 0xFF).toByte() }

    private fun transcript(
        a: ByteArray,
        b: ByteArray,
        pa: ByteArray,
        pb: ByteArray,
        k: ByteArray,
        w: ByteArray,
    ): ByteArray {
        val out = ArrayList<Byte>()
        listOf(a, b, pa, pb, k, w).forEach { field ->
            out.addAll(le64(field.size).toList())
            out.addAll(field.toList())
        }
        return out.toByteArray()
    }

    private fun ke(tt: ByteArray): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(tt).copyOf(Spake2P256.KE_LENGTH)
    }

    @Test
    fun vectorWithServerAndClientIdentities() {
        val w = hex("2ee57912099d31560b3a44b1184b9b4866e904c49d12ac5042c97dca461b1a5f")
        val x = hex("43dd0fd7215bdcb482879fca3220c6a968e66d70b1356cac18bb26c84a78d729")
        val y = hex("dcb60106f276b02606d8ef0a328c02e4b629f84f89786af5befb0bc75b6e66be")

        val (pa, pb) = shares(w, x, y)
        assertEquals(
            "04a56fa807caaa53a4d28dbb9853b9815c61a411118a6fe516a8798434751470f9" +
                "010153ac33d0d5f2047ffdb1a3e42c9b4e6be662766e1eeb4116988ede5f912c",
            pa.hex(),
        )
        assertEquals(
            "0406557e482bd03097ad0cbaa5df82115460d951e3451962f1eaf4367a420676d0" +
                "9857ccbc522686c83d1852abfa8ed6e4a1155cf8f1543ceca528afb591a1e0b7",
            pb.hex(),
        )

        val k = sharedK(w, x, pb)
        assertEquals(
            "0412af7e89717850671913e6b469ace67bd90a4df8ce45c2af19010175e37eed69" +
                "f75897996d539356e2fa6a406d528501f907e04d97515fbe83db277b715d3325",
            k.hex(),
        )

        val tt =
            transcript(
                a = "server".encodeToByteArray(),
                b = "client".encodeToByteArray(),
                pa = pa,
                pb = pb,
                k = k,
                w = w,
            )
        assertEquals("0e0672dc86f8e45565d338b0540abe69", ke(tt).hex())
    }

    @Test
    fun vectorWithBothIdentitiesAbsent() {
        // This is the exact shape CrossPaste uses in production: empty PAKE
        // identities, with identity binding carried by w (the pinContext) and the
        // app-layer confirmation MACs.
        val w = hex("7bf46c454b4c1b25799527d896508afd5fc62ef4ec59db1efb49113063d70cca")
        val x = hex("8cef65df64bb2d0f83540c53632de911b5b24b3eab6cc74a97609fd659e95473")
        val y = hex("d7a66f64074a84652d8d623a92e20c9675c61cb5b4f6a0063e4648a2fdc02d53")

        val (pa, pb) = shares(w, x, y)
        val k = sharedK(w, x, pb)
        val tt = transcript(ByteArray(0), ByteArray(0), pa, pb, k, w)
        // Matching Ke proves pA, pB, K, and the transcript are all correct.
        assertEquals("fc6374762ba5cf11f4b2caa08b2cd1b9", ke(tt).hex())
    }

    @Test
    fun bothPartiesDeriveTheSameSharedElement() {
        val w = hex("2ee57912099d31560b3a44b1184b9b4866e904c49d12ac5042c97dca461b1a5f")
        val x = hex("43dd0fd7215bdcb482879fca3220c6a968e66d70b1356cac18bb26c84a78d729")
        val y = hex("dcb60106f276b02606d8ef0a328c02e4b629f84f89786af5befb0bc75b6e66be")
        val (pa, pb) = shares(w, x, y)

        // A: K = x*(pB - w*N); B: K = y*(pA - w*M). They must match.
        val ka = ec.mulPoint(ec.subtractPoints(pb, ec.mulPoint(n, w)), x)
        val kb = ec.mulPoint(ec.subtractPoints(pa, ec.mulPoint(m, w)), y)
        assertEquals(ka.hex(), kb.hex())
    }
}
