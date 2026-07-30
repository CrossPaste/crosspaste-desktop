package com.crosspaste.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AppTokenServiceTest {

    private fun createService(): TestAppTokenService = TestAppTokenService()

    @Test
    fun `initial token is 6-digit all-zeros`() {
        val service = createService()
        val token = service.token.value
        assertEquals(6, token.size)
        assertTrue(token.all { it == '0' })
    }

    @Test
    fun `sameToken matches when integer equals concatenated token digits`() {
        val service = createService()
        // Initial token is all zeros -> "000000" -> 0
        assertTrue(service.sameToken(0))
    }

    @Test
    fun `sameToken rejects non-matching integer`() {
        val service = createService()
        assertFalse(service.sameToken(123456))
    }

    @Test
    fun `token characters are all decimal digits`() {
        val service = createService()
        assertTrue(service.token.value.all { it in '0'..'9' })
    }

    @Test
    fun `releaseVerifier releases exactly one count per pending verifier`() {
        val service = createService()
        runBlocking {
            service.addPendingVerifier("a")
            service.startRefresh(showToken = true)
            service.addPendingVerifier("b")
            service.startRefresh(showToken = true)
            withTimeout(5.seconds) {
                service.refresh.first { it }
                service.pendingVerifiers.first { it == setOf("a", "b") }
            }

            service.releaseVerifier("a")
            withTimeout(5.seconds) {
                service.pendingVerifiers.first { "a" !in it }
            }

            // Releasing an already-released or unknown verifier must not
            // consume the count still owned by "b".
            service.releaseVerifier("a")
            service.releaseVerifier("unknown")
            delay(200.milliseconds)
            assertTrue(service.refresh.value)

            service.releaseVerifier("b")
            withTimeout(5.seconds) {
                service.refresh.first { !it }
                service.pendingVerifiers.first { it.isEmpty() }
            }
        }
    }

    @Test
    fun `releaseVerifier hideToken hides overlay while other counts keep refreshing`() {
        val service = createService()
        runBlocking {
            // One verifier-owned count plus one anonymous count (e.g. the local
            // pairing-code screen keeping the token rotation alive).
            service.addPendingVerifier("a")
            service.startRefresh(showToken = true)
            service.startRefresh(showToken = false)
            withTimeout(5.seconds) {
                service.showToken.first { it }
                service.refresh.first { it }
            }

            service.releaseVerifier("a", hideToken = true)

            withTimeout(5.seconds) {
                service.showToken.first { !it }
            }
            // The anonymous count is untouched: the refresh loop keeps running.
            assertTrue(service.refresh.value)
        }
    }
}
