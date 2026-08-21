package com.crosspaste.cli.commands

import com.crosspaste.cli.api.CliClientException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TokenCommandTest {

    private class RecordingIo {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val io = TokenIo(stdout = { out.add(it) }, stderr = { err.add(it) })
    }

    private fun request(
        id: String = "peer-1",
        name: String? = "Laptop",
        token: String = "123456",
    ) = PairRequestSummary(appInstanceId = id, deviceName = name, token = token)

    private fun farDeadline() = TestTimeSource().markNow() + 600.seconds

    @Test
    fun activeRequestPrintsOnlyTheTokenToStdout() =
        runTest {
            val recording = RecordingIo()
            val exit =
                executeToken(
                    wait = false,
                    timeoutSeconds = 600,
                    deadline = farDeadline(),
                    json = false,
                    io = recording.io,
                ) { PairTokenSnapshot(listOf(request())) }
            assertEquals(0, exit)
            assertEquals(listOf("123456"), recording.out)
            assertEquals(
                listOf(
                    "Pairing request from 'Laptop':",
                    "Enter this code on the initiating device.",
                ),
                recording.err,
            )
        }

    @Test
    fun concurrentRequestsEachPrintTheirOwnToken() =
        runTest {
            val recording = RecordingIo()
            val exit =
                executeToken(
                    wait = false,
                    timeoutSeconds = 600,
                    deadline = farDeadline(),
                    json = false,
                    io = recording.io,
                ) {
                    PairTokenSnapshot(
                        listOf(
                            request(id = "peer-1", name = "Laptop", token = "111111"),
                            request(id = "peer-2", name = null, token = "222222"),
                        ),
                    )
                }
            assertEquals(0, exit)
            assertEquals(listOf("111111", "222222"), recording.out)
            assertContains(recording.err, "Pairing request from 'Laptop':")
            assertContains(recording.err, "Pairing request from 'peer-2':")
            assertContains(recording.err, "Enter each code on its initiating device.")
        }

    @Test
    fun noRequestWithoutWaitFailsWithGuidanceOnStderr() =
        runTest {
            val recording = RecordingIo()
            val exit =
                executeToken(
                    wait = false,
                    timeoutSeconds = 600,
                    deadline = farDeadline(),
                    json = false,
                    io = recording.io,
                ) { PairTokenSnapshot() }
            assertEquals(1, exit)
            assertTrue(recording.out.isEmpty())
            assertContains(recording.err.single(), "No pairing in progress")
        }

    @Test
    fun waitReturnsTheTokenWhenARequestArrives() =
        runTest {
            val timeSource = TestTimeSource()
            val recording = RecordingIo()
            var calls = 0
            val exit =
                executeToken(
                    wait = true,
                    timeoutSeconds = 600,
                    deadline = timeSource.markNow() + 600.seconds,
                    json = false,
                    io = recording.io,
                    pollInterval = 500.milliseconds,
                    sleep = { timeSource += it },
                ) {
                    calls++
                    if (calls >= 3) PairTokenSnapshot(listOf(request())) else PairTokenSnapshot()
                }
            assertEquals(0, exit)
            assertEquals(listOf("123456"), recording.out)
            assertContains(recording.err, "Waiting for a pairing request... (Ctrl-C to stop)")
        }

    @Test
    fun waitTimesOutAtTheWallClockDeadline() =
        runTest {
            val timeSource = TestTimeSource()
            val recording = RecordingIo()
            var calls = 0
            val exit =
                executeToken(
                    wait = true,
                    timeoutSeconds = 2,
                    deadline = timeSource.markNow() + 2.seconds,
                    json = false,
                    io = recording.io,
                    pollInterval = 500.milliseconds,
                    sleep = { timeSource += it },
                ) {
                    calls++
                    PairTokenSnapshot()
                }
            assertEquals(1, exit)
            assertTrue(recording.out.isEmpty())
            assertContains(recording.err.last(), "No pairing request arrived within 2s.")
            // 2s budget at 500ms per poll = the initial fetch plus exactly 4 polls
            assertEquals(5, calls)
        }

    @Test
    fun jsonPrintsTheSnapshotOnSuccess() =
        runTest {
            val recording = RecordingIo()
            val exit =
                executeToken(
                    wait = false,
                    timeoutSeconds = 600,
                    deadline = farDeadline(),
                    json = true,
                    io = recording.io,
                ) { PairTokenSnapshot(listOf(request())) }
            assertEquals(0, exit)
            assertContains(recording.out.single(), "\"token\": \"123456\"")
        }

    @Test
    fun jsonPrintsAnEmptySnapshotOnWaitTimeout() =
        runTest {
            val timeSource = TestTimeSource()
            val recording = RecordingIo()
            val exit =
                executeToken(
                    wait = true,
                    timeoutSeconds = 1,
                    deadline = timeSource.markNow() + 1.seconds,
                    json = true,
                    io = recording.io,
                    pollInterval = 500.milliseconds,
                    sleep = { timeSource += it },
                ) { PairTokenSnapshot() }
            assertEquals(1, exit)
            // The JSON contract stays uniform: stdout carries a snapshot even on
            // timeout (cliJson omits defaults, so an empty snapshot is "{}")
            assertEquals("{}", recording.out.single())
        }

    @Test
    fun oldAppRouteMissing404MapsToAnUpdateHint() =
        runTest {
            val recording = RecordingIo()
            val exit =
                executeToken(
                    wait = false,
                    timeoutSeconds = 600,
                    deadline = farDeadline(),
                    json = false,
                    io = recording.io,
                ) { throw CliClientException("nf", statusCode = 404, hasServerMessage = false) }
            assertEquals(1, exit)
            assertTrue(recording.out.isEmpty())
            assertContains(recording.err.single(), "too old")
        }

    @Test
    fun otherClientErrorsPropagateToTheRunCliHandler() =
        runTest {
            val recording = RecordingIo()
            assertFailsWith<CliClientException> {
                executeToken(
                    wait = false,
                    timeoutSeconds = 600,
                    deadline = farDeadline(),
                    json = false,
                    io = recording.io,
                ) { throw CliClientException("boom", statusCode = 500, hasServerMessage = true) }
            }
        }

    @Test
    fun awaitGivesUpOnceTheDeadlinePasses() =
        runTest {
            val timeSource = TestTimeSource()
            var calls = 0
            val result =
                awaitPairingRequests(
                    deadline = timeSource.markNow() + 2.seconds,
                    pollInterval = 500.milliseconds,
                    sleep = { timeSource += it },
                ) {
                    calls++
                    PairTokenSnapshot()
                }
            assertNull(result)
            assertEquals(4, calls)
        }

    @Test
    fun requesterLabelSanitizesUntrustedNamesAtTheTerminalBoundary() {
        // Explicit char codes: no literal control bytes in source (project rule)
        val esc = 27.toChar()
        val bel = 7.toChar()
        // ESC/OSC/BEL from an mDNS-announced name must never reach the terminal
        assertEquals(
            "evil]0;pwned-name",
            requesterLabel(request(name = "evil$esc]0;pwned$bel-name")),
        )
        // A name that is nothing but control characters falls back to the id
        assertEquals(
            "peer-1",
            requesterLabel(request(name = "$esc$bel")),
        )
        // The id is untrusted too (exchange header); both blank -> placeholder
        assertEquals(
            "unknown device",
            requesterLabel(request(id = "$esc$bel", name = null)),
        )
        assertEquals("peer-1", requesterLabel(request(name = null)))
    }

    @Test
    fun routeMissingDetectionRequiresA404WithoutAServerMessage() {
        assertEquals(
            true,
            isTokenRouteMissing(CliClientException("nf", statusCode = 404, hasServerMessage = false)),
        )
        // A 404 WITH a structured message is the endpoint answering, not a missing route
        assertEquals(
            false,
            isTokenRouteMissing(CliClientException("nf", statusCode = 404, hasServerMessage = true)),
        )
        assertEquals(
            false,
            isTokenRouteMissing(CliClientException("boom", statusCode = 500, hasServerMessage = false)),
        )
        assertEquals(
            false,
            isTokenRouteMissing(CliClientException("io", statusCode = null, hasServerMessage = false)),
        )
    }

    @Test
    fun noPairingMessageDistinguishesTimeoutFromNoAttempt() {
        assertEquals(
            "No pairing request arrived within 30s.",
            noPairingMessage(waited = true, timeoutSeconds = 30),
        )
        assertEquals(
            "No pairing in progress. Start pairing from the other device " +
                "(crosspaste pair), or rerun with --wait.",
            noPairingMessage(waited = false, timeoutSeconds = 30),
        )
    }
}
