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
        fileName: String = CliConfigReader.CLI_ENDPOINT_FILE_NAME,
    ) {
        FileSystem.SYSTEM.write(dir.resolve(fileName)) {
            writeUtf8(
                """{"pid":$pid,"socketPath":"$socketPath","apiVersion":1,"appInstanceId":"$appInstanceId"}""",
            )
        }
    }

    private fun writeDevEndpoint(
        dir: Path,
        pid: Long = 5678,
        appInstanceId: String = "dev-instance",
    ) = writeEndpoint(
        dir,
        pid = pid,
        appInstanceId = appInstanceId,
        fileName = CliConfigReader.CLI_DEV_ENDPOINT_FILE_NAME,
    )

    private fun checker(
        dir: Path,
        socketProber: suspend (CliEndpoint) -> Boolean = { false },
        processAlive: (Long) -> Boolean = { false },
        configReader: CliConfigReader = CliConfigReader(FakePathProvider(dir)),
    ): AppReadinessChecker =
        AppReadinessChecker(
            configReader = configReader,
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
    fun probeFallsBackToLiveDevEndpointWhenPrimaryIsMissing() =
        runTest {
            val dir = tempDir()
            writeDevEndpoint(dir)
            val configReader = CliConfigReader(FakePathProvider(dir))
            val liveness =
                checker(
                    dir,
                    socketProber = { it.appInstanceId == "dev-instance" },
                    configReader = configReader,
                ).probe()
            assertEquals(AppLiveness.RUNNING, liveness)
            assertTrue(configReader.devEndpointActive)
            assertEquals(configReader.devEndpointFilePath(), configReader.resolveEndpointFilePath())
        }

    @Test
    fun probePrefersLivePrimaryOverLiveDevEndpoint() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir, appInstanceId = "installed-instance")
            writeDevEndpoint(dir)
            val configReader = CliConfigReader(FakePathProvider(dir))
            val liveness =
                checker(dir, socketProber = { true }, configReader = configReader).probe()
            assertEquals(AppLiveness.RUNNING, liveness)
            assertFalse(configReader.devEndpointActive)
        }

    @Test
    fun probeFallsBackToDevEndpointOverStartingPrimary() =
        runTest {
            val dir = tempDir()
            writeEndpoint(dir, pid = 4321, appInstanceId = "installed-instance")
            writeDevEndpoint(dir)
            val configReader = CliConfigReader(FakePathProvider(dir))
            val liveness =
                checker(
                    dir,
                    socketProber = { it.appInstanceId == "dev-instance" },
                    processAlive = { true },
                    configReader = configReader,
                ).probe()
            assertEquals(AppLiveness.RUNNING, liveness)
            assertTrue(configReader.devEndpointActive)
        }

    @Test
    fun probeIgnoresStaleDevEndpoint() =
        runTest {
            val dir = tempDir()
            // The dev pointer is only ever written once the dev socket is
            // live, so a dead socket means a stale leftover, never STARTING
            writeDevEndpoint(dir)
            val configReader = CliConfigReader(FakePathProvider(dir))
            val liveness =
                checker(
                    dir,
                    socketProber = { false },
                    processAlive = { true },
                    configReader = configReader,
                ).probe()
            assertEquals(AppLiveness.NOT_RUNNING, liveness)
            assertFalse(configReader.devEndpointActive)
        }

    @Test
    fun probeSwitchesBackToPrimaryWhenItComesAlive() =
        runTest {
            val dir = tempDir()
            writeDevEndpoint(dir)
            val configReader = CliConfigReader(FakePathProvider(dir))
            val readiness = checker(dir, socketProber = { true }, configReader = configReader)
            assertEquals(AppLiveness.RUNNING, readiness.probe())
            assertTrue(configReader.devEndpointActive)

            writeEndpoint(dir, appInstanceId = "installed-instance")
            assertEquals(AppLiveness.RUNNING, readiness.probe())
            assertFalse(configReader.devEndpointActive)
        }

    @Test
    fun waitForAppReadyPicksUpDevEndpointAppearingMidWait() =
        runTest {
            val dir = tempDir()
            val configReader = CliConfigReader(FakePathProvider(dir))
            launch {
                kotlinx.coroutines.delay(2.seconds)
                writeDevEndpoint(dir)
            }
            assertTrue(
                checker(dir, socketProber = { true }, configReader = configReader).waitForAppReady(),
            )
            assertTrue(configReader.devEndpointActive)
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
