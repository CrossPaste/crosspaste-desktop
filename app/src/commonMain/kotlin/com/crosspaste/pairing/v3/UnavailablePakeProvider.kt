package com.crosspaste.pairing.v3

/**
 * Explicit fallback for platforms that do not yet provide a reviewed [PakeEcOps]
 * backend. Production wiring uses this until every target has passed the
 * constant-time review. Unsupported targets fail closed instead of substituting
 * a fake or test-only PAKE.
 */
object UnavailablePakeProvider : PakeProvider {

    override val ciphersuite: String = PairingV3.CIPHERSUITE_SPAKE2_P256

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession = throw PakeException("pairing v3 PAKE provider is not available yet")
}
