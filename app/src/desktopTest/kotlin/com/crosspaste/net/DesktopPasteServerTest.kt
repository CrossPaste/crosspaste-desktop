package com.crosspaste.net

import com.crosspaste.config.TestReadWritePort
import com.crosspaste.net.exception.ExceptionHandler
import io.ktor.server.netty.NettyApplicationEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopPasteServerTest {

    @Test
    fun startPropagatesFailureWhenConfiguredAndFallbackPortsBothFail() =
        runTest {
            val startupFailure = IllegalStateException("server factory unavailable")
            val exceptionHandler =
                mockk<ExceptionHandler> {
                    every { isPortAlreadyInUse(any()) } returns true
                }
            val serverFactory =
                mockk<
                    ServerFactory<
                        NettyApplicationEngine,
                        NettyApplicationEngine.Configuration,
                    >,
                > {
                    every { getFactory() } throws startupFailure
                }
            val server =
                DesktopPasteServer(
                    readWritePort = TestReadWritePort().apply { port = 12_345 },
                    exceptionHandler = exceptionHandler,
                    serverFactory = serverFactory,
                    serverModule = mockk(relaxed = true),
                )

            val thrown = assertFailsWith<IllegalStateException> { server.start() }

            assertEquals(startupFailure.message, thrown.message)
            verify(exactly = 2) { serverFactory.getFactory() }
        }
}
