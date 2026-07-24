package com.crosspaste.pairing.v3

/**
 * Explicit fallback for platforms that do not yet provide a reviewed [PakeEcOps]
 * backend. Desktop uses the real BouncyCastle provider; this remains available to
 * shared/mobile wiring until every Kotlin/Native target has its production EC
 * backend. Unsupported targets fail closed instead of substituting a fake PAKE.
 */
object UnavailablePakeProvider : PakeProvider {

    override val ciphersuite: String = PairingV3.CIPHERSUITE_SPAKE2_P256

    override suspend fun createSession(
        role: PakeRole,
        pin: CharArray,
        context: PakeContext,
    ): PakeSession = throw PakeException("pairing v3 PAKE provider is not available yet")
}
