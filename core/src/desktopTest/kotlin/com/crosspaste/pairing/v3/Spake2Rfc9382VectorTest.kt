package com.crosspaste.pairing.v3

/**
 * Runs the shared RFC 9382 P-256 vectors through [BouncyCastlePakeEcOps] —
 * the correctness gate for the JVM EC backend.
 */
class Spake2Rfc9382VectorTest : AbstractSpake2Rfc9382VectorTest() {

    override val ec: PakeEcOps = BouncyCastlePakeEcOps()
}
