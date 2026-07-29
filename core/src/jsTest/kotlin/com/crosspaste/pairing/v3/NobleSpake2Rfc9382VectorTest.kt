package com.crosspaste.pairing.v3

/**
 * Runs the shared RFC 9382 P-256 vectors through [NoblePakeEcOps] — the
 * correctness gate for the browser (@noble/curves) EC backend used by the
 * extension's pairing v3 client.
 */
class NobleSpake2Rfc9382VectorTest : AbstractSpake2Rfc9382VectorTest() {

    override val ec: PakeEcOps = NoblePakeEcOps()
}
