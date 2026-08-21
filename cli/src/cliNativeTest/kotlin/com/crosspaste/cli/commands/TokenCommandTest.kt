package com.crosspaste.cli.commands

import com.crosspaste.cli.api.CliClientException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TokenCommandTest {

    private fun snapshot(active: Boolean) =
        PairTokenSnapshot(
            active = active,
            token = if (active) "123456" else null,
            requesters = listOf(),
        )

    @Test
    fun awaitReturnsTheFirstActiveSnapshot() =
        runTest {
            var calls = 0
            val result =
                awaitActiveSnapshot(
                    timeout = 2.seconds,
                    pollInterval = 500.milliseconds,
                    sleep = {},
                ) {
                    calls++
                    snapshot(active = calls == 3)
                }
            assertEquals("123456", result?.token)
            assertEquals(3, calls)
        }

    @Test
    fun awaitGivesUpAfterTheTimeoutBudget() =
        runTest {
            var calls = 0
            val result =
                awaitActiveSnapshot(
                    timeout = 2.seconds,
                    pollInterval = 500.milliseconds,
                    sleep = {},
                ) {
                    calls++
                    snapshot(active = false)
                }
            assertNull(result)
            // 2s budget at 500ms per poll = exactly 4 attempts
            assertEquals(4, calls)
        }

    @Test
    fun awaitAlwaysPollsAtLeastOnce() =
        runTest {
            var calls = 0
            awaitActiveSnapshot(
                timeout = 100.milliseconds,
                pollInterval = 500.milliseconds,
                sleep = {},
            ) {
                calls++
                snapshot(active = false)
            }
            assertEquals(1, calls)
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

    @Test
    fun describeRequestersPrefersDeviceNamesAndFallsBackToIds() {
        assertNull(describeRequesters(listOf()))
        assertEquals(
            "Pairing request from 'Laptop'.",
            describeRequesters(listOf(PairRequesterSummary("id-1", "Laptop"))),
        )
        assertEquals(
            "Pairing request from 'Laptop', 'id-2'.",
            describeRequesters(
                listOf(
                    PairRequesterSummary("id-1", "Laptop"),
                    PairRequesterSummary("id-2", null),
                ),
            ),
        )
    }
}
