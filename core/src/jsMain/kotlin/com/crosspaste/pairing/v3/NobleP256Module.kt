@file:JsModule("@noble/curves/p256")

package com.crosspaste.pairing.v3

/**
 * The `p256` curve object from the audited `@noble/curves` library
 * (short Weierstrass secp256r1). Accessed as `dynamic`: the exact member
 * surface (`ProjectivePoint`, `CURVE.n`) is pinned at runtime by the
 * RFC 9382 vector tests in jsTest, not by static bindings.
 */
external val p256: dynamic
