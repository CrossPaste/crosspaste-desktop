package com.crosspaste.mcp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopMcpServerTest {

    @Test
    fun `start is idempotent and stop clears the published port`() =
        runTest {
            val factory = RecordingMcpServerFactory()
            val server = createServer(factory)

            server.start()
            server.start()

            assertEquals(DesktopMcpServer.DEFAULT_PORT, server.port())
            assertEquals(listOf("create:13130", "start:13130"), factory.events)

            server.stop()

            assertEquals(0, server.port())
            assertEquals(listOf("create:13130", "start:13130", "stop:13130"), factory.events)
        }

    @Test
    fun `failed replacement keeps the existing listener and port`() =
        runTest {
            val factory = RecordingMcpServerFactory(failStartPorts = setOf(13131))
            val server = createServer(factory)
            server.start()

            assertFailsWith<IllegalStateException> {
                server.restart(13131)
            }

            assertEquals(DesktopMcpServer.DEFAULT_PORT, server.port())
            assertEquals(
                0,
                factory.instances
                    .getValue(13130)
                    .single()
                    .stopCount,
            )
            assertEquals(
                1,
                factory.instances
                    .getValue(13131)
                    .single()
                    .stopCount,
            )
        }

    @Test
    fun `failed initial start cleans the candidate and does not publish a port`() =
        runTest {
            val factory = RecordingMcpServerFactory(failStartPorts = setOf(13130))
            val server = createServer(factory)

            assertFailsWith<IllegalStateException> {
                server.start()
            }

            assertEquals(0, server.port())
            assertEquals(
                1,
                factory.instances
                    .getValue(13130)
                    .single()
                    .stopCount,
            )
        }

    @Test
    fun `restart binds replacement before stopping current listener`() =
        runTest {
            val factory = RecordingMcpServerFactory()
            val server = createServer(factory)
            server.start()

            server.restart(13131)

            assertEquals(13131, server.port())
            assertEquals(
                listOf("create:13130", "start:13130", "create:13131", "start:13131", "stop:13130"),
                factory.events,
            )
        }

    @Test
    fun `stop waits for an in-flight start and closes the started listener`() =
        runTest {
            val startEntered = CompletableDeferred<Unit>()
            val allowStart = CompletableDeferred<Unit>()
            val factory =
                RecordingMcpServerFactory(
                    beforeStart = { port ->
                        if (port == DesktopMcpServer.DEFAULT_PORT) {
                            startEntered.complete(Unit)
                            allowStart.await()
                        }
                    },
                )
            val server = createServer(factory)

            val startJob = launch { server.start() }
            startEntered.await()
            val stopJob = launch { server.stop() }
            runCurrent()

            assertEquals(
                0,
                factory.instances
                    .getValue(13130)
                    .single()
                    .stopCount,
            )

            allowStart.complete(Unit)
            startJob.join()
            stopJob.join()

            assertEquals(0, server.port())
            assertEquals(
                1,
                factory.instances
                    .getValue(13130)
                    .single()
                    .stopCount,
            )
        }

    @Test
    fun `concurrent restarts are serialized in request order`() =
        runTest {
            val replacementStartEntered = CompletableDeferred<Unit>()
            val allowReplacementStart = CompletableDeferred<Unit>()
            val factory =
                RecordingMcpServerFactory(
                    beforeStart = { port ->
                        if (port == 13131) {
                            replacementStartEntered.complete(Unit)
                            allowReplacementStart.await()
                        }
                    },
                )
            val server = createServer(factory)
            server.start()

            val firstRestart = launch { server.restart(13131) }
            replacementStartEntered.await()
            val secondRestart = launch { server.restart(13132) }
            runCurrent()

            allowReplacementStart.complete(Unit)
            firstRestart.join()
            secondRestart.join()

            assertEquals(13132, server.port())
            assertEquals(
                1,
                factory.instances
                    .getValue(13130)
                    .single()
                    .stopCount,
            )
            assertEquals(
                1,
                factory.instances
                    .getValue(13131)
                    .single()
                    .stopCount,
            )
            assertEquals(
                0,
                factory.instances
                    .getValue(13132)
                    .single()
                    .stopCount,
            )
        }

    @Test
    fun `stop queued behind restart closes the replacement`() =
        runTest {
            val replacementStartEntered = CompletableDeferred<Unit>()
            val allowReplacementStart = CompletableDeferred<Unit>()
            val factory =
                RecordingMcpServerFactory(
                    beforeStart = { port ->
                        if (port == 13131) {
                            replacementStartEntered.complete(Unit)
                            allowReplacementStart.await()
                        }
                    },
                )
            val server = createServer(factory)
            server.start()

            val restartJob = launch { server.restart(13131) }
            replacementStartEntered.await()
            val stopJob = launch { server.stop() }
            runCurrent()

            allowReplacementStart.complete(Unit)
            restartJob.join()
            stopJob.join()

            assertEquals(0, server.port())
            assertEquals(
                1,
                factory.instances
                    .getValue(13130)
                    .single()
                    .stopCount,
            )
            assertEquals(
                1,
                factory.instances
                    .getValue(13131)
                    .single()
                    .stopCount,
            )
        }

    private fun kotlinx.coroutines.test.TestScope.createServer(factory: McpServerInstanceFactory): DesktopMcpServer =
        DesktopMcpServer(
            configuredPort = 0,
            serverFactory = factory,
            lifecycleDispatcher = StandardTestDispatcher(testScheduler),
        )
}

private class RecordingMcpServerFactory(
    private val failStartPorts: Set<Int> = emptySet(),
    private val beforeStart: suspend (Int) -> Unit = {},
) : McpServerInstanceFactory {

    val events = mutableListOf<String>()
    val instances = mutableMapOf<Int, MutableList<RecordingMcpServerInstance>>()

    override fun create(port: Int): McpServerInstance {
        events += "create:$port"
        return RecordingMcpServerInstance(
            port = port,
            events = events,
            failStart = port in failStartPorts,
            beforeStart = beforeStart,
        ).also { instance ->
            instances.getOrPut(port) { mutableListOf() } += instance
        }
    }
}

private class RecordingMcpServerInstance(
    private val port: Int,
    private val events: MutableList<String>,
    private val failStart: Boolean,
    private val beforeStart: suspend (Int) -> Unit,
) : McpServerInstance {

    var stopCount = 0
        private set

    override suspend fun start() {
        events += "start:$port"
        beforeStart(port)
        if (failStart) {
            throw IllegalStateException("Port $port is unavailable")
        }
    }

    override suspend fun stop() {
        events += "stop:$port"
        stopCount++
    }
}
