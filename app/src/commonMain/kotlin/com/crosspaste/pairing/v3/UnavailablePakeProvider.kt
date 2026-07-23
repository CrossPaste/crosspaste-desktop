package com.crosspaste.pairing.v3

/**
 * Production placeholder until the real RFC 9382 SPAKE2 provider lands (ADR D7).
 *
 * Pairing v3 is intentionally inert in production: the capability advertisement
 * still announces pairing v2 and the acceptance window defaults to closed, so no
 * compliant peer reaches PAKE creation. If something does, failing loudly here is
 * strictly better than silently running a non-reviewed PAKE.
 */
object UnavailablePakeProvider : PakeProvider {

    override val ciphersuite: String = PairingV3.CIPHERSUITE_SPAKE2_P256

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession = throw PakeException("pairing v3 PAKE provider is not available yet")
}
