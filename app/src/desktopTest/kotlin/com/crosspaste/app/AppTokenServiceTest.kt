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

    private fun AppTokenService.currentToken(): Int = token.value.concatToString().toInt()

    @Test
    fun `sameToken rejects the current token while no pairing session is open`() {
        val service = createService()
        assertFalse(service.sameToken(service.currentToken()))
        assertFalse(service.sameToken(0))
    }

    @Test
    fun `sameToken accepts the displayed token only while a pairing session is open`() {
        val service = createService()
        runBlocking {
            service.startRefresh(showToken = false)
            withTimeout(5.seconds) { service.refresh.first { it } }
            val displayed = service.currentToken()
            assertTrue(service.sameToken(displayed))
            assertFalse(service.sameToken((displayed + 1) % 1_000_000))

            service.stopRefresh(hideToken = false)
            withTimeout(5.seconds) { service.refresh.first { !it } }
            assertFalse(service.sameToken(displayed))
        }
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
            service.acquireVerifier("a")
            service.acquireVerifier("b")
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
    fun `release enqueued right after acquire cannot orphan the refresh count`() {
        val service = createService()
        runBlocking {
            service.acquireVerifier("a")
            service.releaseVerifier("a")
            // FIFO marker: once it is visible, every prior command has run.
            service.acquireVerifier("marker")
            withTimeout(5.seconds) {
                service.pendingVerifiers.first { "marker" in it }
            }
            assertEquals(setOf("marker"), service.pendingVerifiers.value)
            service.releaseVerifier("marker")
            withTimeout(5.seconds) {
                service.refresh.first { !it }
            }
        }
    }

    @Test
    fun `repeated acquire by one verifier owns only one refresh count`() {
        val service = createService()
        runBlocking {
            service.acquireVerifier("a")
            service.acquireVerifier("a")
            service.releaseVerifier("a")
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
            service.acquireVerifier("a")
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
