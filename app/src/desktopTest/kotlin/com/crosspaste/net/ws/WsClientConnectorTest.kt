package com.crosspaste.net.ws

import com.crosspaste.app.AppInfo
import com.crosspaste.secure.SecureStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class WsClientConnectorTest {

    @Test
    fun connectAsync_deduplicatesInFlightHandshakeAndReleasesOnFailure() =
        runBlocking {
            val attempts = AtomicInteger(0)
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()

            val client =
                HttpClient(
                    MockEngine {
                        val attempt = attempts.incrementAndGet()
                        if (attempt == 1) {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                        } else {
                            secondStarted.complete(Unit)
                        }
                        respondError(HttpStatusCode.ServiceUnavailable)
                    },
                ) {
                    install(WebSockets)
                }

            val appInfo: AppInfo =
                mockk(relaxed = true) {
                    every { appInstanceId } returns "local-peer"
                }
            val connector =
                WsClientConnector(
                    appInfo = appInfo,
                    client = client,
                    secureStore = mockk<SecureStore>(relaxed = true),
                    wsSessionManager = WsSessionManager(),
                    wsMessageHandler = mockk<WsMessageHandler>(relaxed = true),
                )

            try {
                connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                withTimeout(5.seconds) { firstStarted.await() }

                // Duplicate dial while the first handshake is still in flight must be ignored.
                connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                assertEquals(1, attempts.get())

                // Release first handshake so it fails and frees the in-flight slot.
                releaseFirst.complete(Unit)

                withTimeout(5.seconds) {
                    while (attempts.get() < 2) {
                        connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                        if (attempts.get() < 2) {
                            kotlinx.coroutines.delay(10)
                        }
                    }
                    secondStarted.await()
                }
                assertEquals(2, attempts.get())
            } finally {
                client.close()
            }
        }

    @Test
    fun connectAsync_endpointChangeReplacesInFlightHandshakeImmediately() =
        runBlocking {
            val oldPortStarted = CompletableDeferred<Unit>()
            val oldPortCancelled = CompletableDeferred<Unit>()
            val newPortStarted = CompletableDeferred<Int>()

            val client =
                HttpClient(
                    MockEngine { request ->
                        when (request.url.port) {
                            8080 -> {
                                oldPortStarted.complete(Unit)
                                try {
                                    kotlinx.coroutines.awaitCancellation()
                                } finally {
                                    oldPortCancelled.complete(Unit)
                                }
                            }
                            9090 -> {
                                newPortStarted.complete(9090)
                                respondError(HttpStatusCode.ServiceUnavailable)
                            }
                            else -> respondError(HttpStatusCode.BadRequest)
                        }
                    },
                ) {
                    install(WebSockets)
                }

            val appInfo: AppInfo =
                mockk(relaxed = true) {
                    every { appInstanceId } returns "local-peer"
                }
            val connector =
                WsClientConnector(
                    appInfo = appInfo,
                    client = client,
                    secureStore = mockk<SecureStore>(relaxed = true),
                    wsSessionManager = WsSessionManager(),
                    wsMessageHandler = mockk<WsMessageHandler>(relaxed = true),
                )

            try {
                connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                withTimeout(5.seconds) { oldPortStarted.await() }

                // Endpoint changes while old handshake is still blocked: must cancel old and dial new immediately.
                connector.connectAsync("127.0.0.1", 9090, "remote-peer")

                assertEquals(9090, withTimeout(5.seconds) { newPortStarted.await() })
                withTimeout(5.seconds) { oldPortCancelled.await() }
            } finally {
                client.close()
            }
        }

    @Test
    fun pause_cancelsInFlightHandshakeAndBlocksNewDialsUntilResume() =
        runBlocking {
            val attempts = AtomicInteger(0)
            val firstStarted = CompletableDeferred<Unit>()
            val firstCancelled = CompletableDeferred<Unit>()
            val resumedDialStarted = CompletableDeferred<Unit>()

            val client =
                HttpClient(
                    MockEngine {
                        val attempt = attempts.incrementAndGet()
                        if (attempt == 1) {
                            firstStarted.complete(Unit)
                            try {
                                kotlinx.coroutines.awaitCancellation()
                            } finally {
                                firstCancelled.complete(Unit)
                            }
                        } else {
                            resumedDialStarted.complete(Unit)
                            respondError(HttpStatusCode.ServiceUnavailable)
                        }
                    },
                ) {
                    install(WebSockets)
                }

            val appInfo: AppInfo =
                mockk(relaxed = true) {
                    every { appInstanceId } returns "local-peer"
                }
            val wsSessionManager = WsSessionManager()
            val connector =
                WsClientConnector(
                    appInfo = appInfo,
                    client = client,
                    secureStore = mockk<SecureStore>(relaxed = true),
                    wsSessionManager = wsSessionManager,
                    wsMessageHandler = mockk<WsMessageHandler>(relaxed = true),
                )

            try {
                connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                withTimeout(5.seconds) { firstStarted.await() }

                // Pause must cancel the in-flight handshake and reject new dials.
                wsSessionManager.pause()
                withTimeout(5.seconds) { firstCancelled.await() }

                connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                assertEquals(1, attempts.get())
                assertEquals(false, connector.connect("127.0.0.1", 8080, "remote-peer"))
                assertEquals(1, attempts.get())

                // Resume re-enables outbound handshakes.
                wsSessionManager.resume()
                connector.connectAsync("127.0.0.1", 8080, "remote-peer")
                withTimeout(5.seconds) { resumedDialStarted.await() }
                assertEquals(2, attempts.get())
            } finally {
                client.close()
            }
        }
}
