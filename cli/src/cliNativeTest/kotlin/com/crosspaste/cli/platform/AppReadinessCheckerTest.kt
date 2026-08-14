package com.crosspaste.cli.platform

import com.crosspaste.cli.api.CliEndpoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private class FakePathProvider(
    private val dir: Path,
) : NativePlatformPathProvider {
    override fun getDefaultUserDataPath(): Path = dir
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppReadinessCheckerTest {

    private fun tempDir(): Path {
        val dir =
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                .resolve("cli-readiness-test-${Random.nextBits(31)}")
        FileSystem.SYSTEM.createDirectories(dir)
        return dir
    }

    private fun writeEndpoint(
        dir: Path,
        pid: Long = 1234,
        socketPath: String = "/tmp/does-not-matter.sock",
        appInstanceId: String = "instance-a",
    ) {
        FileSystem.SYSTEM.write(dir.resolve(CliConfigReader.CLI_ENDPOINT_FILE_NAME)) {
            writeUtf8(
                """{"pid":$pid,"socketPath":"$socketPath","apiVersion":1,"appInstanceId":"$appInstanceId"}""",
            )
        }
    }

    private fun checker(
        dir: Path,
        socketProber: suspend (CliEndpoint) -> Boolean = { false },
        processAlive: (Long) -> Boolean = { false },
    ): AppReadinessChecker =
        AppReadinessChecker(
            configReader = CliConfigReader(FakePathProvider(dir)),
            socketProber = socketProber,
            processAlive = processAlive,
        )

    @Test
    fun probeReportsNotRunningWithoutEndpointFile() =
        runTest {
            val dir = tempDir()
            assertEquals(AppLiveness.NOT_RUNNING, checker(dir, socketProber = { true }).probe())
        }

    @Test
    fun probeReportsNotRunningForCorruptedEndpointFile() =
        runTest {
            val dir = tempDir()
            FileSystem.SYSTEM.write(dir.resolve(CliConfigReader.CLI_ENDPOINT_FILE_NAME)) {
                writeUtf8("not json at all")
            }
            assertEquals(AppLiveness.NOT_RUNNING, checker(dir, socketProber = { true }).probe())
        }

    @Test
    fun probeReportsRunningWhenSocketAnswers() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir)
            assertEquals(AppLiveness.RUNNING, checker(dir, socketProber = { true }).probe())
        }

    @Test
    fun probeReportsStartingWhenSocketDeadButPidAlive() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir, pid = 4321)
            val liveness =
                checker(dir, socketProber = { false }, processAlive = { pid -> pid == 4321L }).probe()
            assertEquals(AppLiveness.STARTING, liveness)
        }

    @Test
    fun probeReportsNotRunningForStaleEndpointWithDeadPid() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir, pid = 4321)
            assertEquals(
                AppLiveness.NOT_RUNNING,
                checker(dir, socketProber = { false }, processAlive = { false }).probe(),
            )
        }

    @Test
    fun waitForAppReadyGivesUpAtHardDeadline() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir)
            val start = currentTime
            assertFalse(checker(dir, socketProber = { false }).waitForAppReady())
            assertEquals(30.seconds.inWholeMilliseconds, currentTime - start)
        }

    @Test
    fun waitForAppReadyDeadlineHoldsEvenWhenProbesAreSlow() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir)
            // A server that accepts connections but never answers: each probe
            // burns 2s. The overall deadline must still cap the wait at 30s.
            val start = currentTime
            val ready =
                checker(
                    dir,
                    socketProber = {
                        kotlinx.coroutines.delay(2.seconds)
                        false
                    },
                ).waitForAppReady()
            assertFalse(ready)
            assertEquals(30.seconds.inWholeMilliseconds, currentTime - start)
        }

    @Test
    fun waitForAppReadySucceedsOnceSocketComesUp() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir)
            var attempts = 0
            val ready =
                checker(
                    dir,
                    socketProber = {
                        attempts++
                        attempts >= 3
                    },
                ).waitForAppReady()
            assertTrue(ready)
        }

    @Test
    fun waitForAppReadyRereadsEndpointFileEveryRound() =
        runTest {
            val dir = tempDir()
            // File does not exist yet: the app writes it only once the socket
            // is live, so the checker must pick it up mid-wait.
            launch {
                kotlinx.coroutines.delay(2.seconds)
                writeEndpoint(dir)
            }
            assertTrue(checker(dir, socketProber = { true }).waitForAppReady())
        }

    @Test
    fun matchingInstanceIdIsAccepted() {
        val endpoint = endpoint(appInstanceId = "instance-a")
        assertTrue(
            matchesEndpointInstance("""{"appInstanceId":"instance-a","appVersion":"x"}""", endpoint),
        )
    }

    @Test
    fun differentInstanceIdIsRejected() {
        val endpoint = endpoint(appInstanceId = "instance-a")
        assertFalse(matchesEndpointInstance("""{"appInstanceId":"instance-b"}""", endpoint))
    }

    @Test
    fun unknownStatusFieldsAreTolerated() {
        val endpoint = endpoint(appInstanceId = "instance-a")
        assertTrue(
            matchesEndpointInstance(
                """{"appInstanceId":"instance-a","futureField":123,"nested":{"a":1}}""",
                endpoint,
            ),
        )
    }

    @Test
    fun unparseableStatusBodyIsRejected() {
        val endpoint = endpoint(appInstanceId = "instance-a")
        assertFalse(matchesEndpointInstance("<html>proxy error</html>", endpoint))
        assertFalse(matchesEndpointInstance("""{"noInstanceId":true}""", endpoint))
    }

    private fun endpoint(appInstanceId: String): CliEndpoint =
        CliEndpoint(
            pid = 1,
            socketPath = "/tmp/x.sock",
            apiVersion = 1,
            appInstanceId = appInstanceId,
        )
}
