package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingRateLimiterTest {

    private var now = 0L

    private fun newLimiter(
        maxPerKey: Int = 2,
        maxGlobal: Int = 10,
        windowMillis: Long = 60_000L,
    ) = PairingRateLimiter(
        maxPerKey = maxPerKey,
        maxGlobal = maxGlobal,
        windowMillis = windowMillis,
        nowEpochMillis = { now },
    )

    @Test
    fun testPerFingerprintLimit() =
        runTest {
            val limiter = newLimiter()
            val peerA = PairingAttemptSource(peerKeyFingerprint = "fp-a")

            assertTrue(limiter.tryAcquire(peerA))
            assertTrue(limiter.tryAcquire(peerA))
            assertFalse(limiter.tryAcquire(peerA))
            // Other peers unaffected by peer-a exhaustion
            assertTrue(limiter.tryAcquire(PairingAttemptSource(peerKeyFingerprint = "fp-b")))
        }

    @Test
    fun testGlobalLimitAcrossRotatingIdentities() =
        runTest {
            val limiter = newLimiter(maxPerKey = 2, maxGlobal = 3)

            repeat(3) { index ->
                assertTrue(
                    limiter.tryAcquire(
                        PairingAttemptSource(
                            peerKeyFingerprint = "fp-$index",
                            peerAppInstanceId = "app-$index",
                            remoteAddress = "10.0.0.$index",
                        ),
                    ),
                )
            }
            // Completely fresh identity, but the global window is exhausted
            assertFalse(
                limiter.tryAcquire(
                    PairingAttemptSource(
                        peerKeyFingerprint = "fp-fresh",
                        peerAppInstanceId = "app-fresh",
                        remoteAddress = "10.0.0.99",
                    ),
                ),
            )
        }

    @Test
    fun testOneAttemptCountsGlobalOnceAcrossDimensions() =
        runTest {
            val limiter = newLimiter(maxPerKey = 5, maxGlobal = 2)

            // Each attempt provides three dimensions; if global were counted per
            // dimension the first attempt alone would exhaust maxGlobal = 2
            assertTrue(
                limiter.tryAcquire(
                    PairingAttemptSource("fp-1", "app-1", "10.0.0.1"),
                ),
            )
            assertTrue(
                limiter.tryAcquire(
                    PairingAttemptSource("fp-2", "app-2", "10.0.0.2"),
                ),
            )
            assertFalse(
                limiter.tryAcquire(
                    PairingAttemptSource("fp-3", "app-3", "10.0.0.3"),
                ),
            )
        }

    @Test
    fun testExhaustedDimensionBlocksWithoutConsumingOthers() =
        runTest {
            val limiter = newLimiter(maxPerKey = 2, maxGlobal = 10)
            val sharedAddress = "10.0.0.1"

            // Exhaust the address dimension with two different peers
            assertTrue(limiter.tryAcquire(PairingAttemptSource("fp-x", null, sharedAddress)))
            assertTrue(limiter.tryAcquire(PairingAttemptSource("fp-y", null, sharedAddress)))

            // fp-a is fresh, but the shared address is exhausted: rejected...
            assertFalse(limiter.tryAcquire(PairingAttemptSource("fp-a", null, sharedAddress)))

            // ...and the rejection consumed nothing from fp-a's own budget
            assertTrue(limiter.tryAcquire(PairingAttemptSource("fp-a", null, "10.0.0.2")))
            assertTrue(limiter.tryAcquire(PairingAttemptSource("fp-a", null, "10.0.0.2")))
        }

    @Test
    fun testWindowSlides() =
        runTest {
            val limiter = newLimiter(maxPerKey = 1, maxGlobal = 10, windowMillis = 60_000L)
            val peer = PairingAttemptSource(peerKeyFingerprint = "fp-a")

            assertTrue(limiter.tryAcquire(peer))
            assertFalse(limiter.tryAcquire(peer))

            now += 60_001L
            assertTrue(limiter.tryAcquire(peer))
        }
}
