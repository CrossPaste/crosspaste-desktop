package com.crosspaste.net.ws

import com.crosspaste.app.AppInfo
import com.crosspaste.config.TestReadWritePort
import com.crosspaste.db.secure.MemorySecureIO
import com.crosspaste.net.DesktopPasteServer
import com.crosspaste.net.DesktopServerFactory
import com.crosspaste.net.ServerModule
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.routing.wsRouting
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.test.IntegrationTest
import com.crosspaste.utils.CryptographyUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@IntegrationTest
class WsAuthenticationIntegrationTest {

    @Test
    fun `client and server authenticate before exchanging messages`() =
        runBlocking {
            val serializer = SecureKeyPairSerializer()
            val serverKeys = CryptographyUtils.generateSecureKeyPair()
            val clientKeys = CryptographyUtils.generateSecureKeyPair()
            val serverStore = GeneralSecureStore(serverKeys, serializer, MemorySecureIO())
            val clientStore = GeneralSecureStore(clientKeys, serializer, MemorySecureIO())
            val serverInfo = appInfo("server")
            val clientInfo = appInfo("client")
            serverStore.saveCryptPublicKey(
                clientInfo.appInstanceId,
                serializer.encodeCryptPublicKey(clientKeys.cryptKeyPair.publicKey),
            )
            clientStore.saveCryptPublicKey(
                serverInfo.appInstanceId,
                serializer.encodeCryptPublicKey(serverKeys.cryptKeyPair.publicKey),
            )

            val serverSessions = WsSessionManager()
            val clientSessions = WsSessionManager()
            val serverHandler = mockk<WsMessageHandler>(relaxed = true)
            val clientHandler = mockk<WsMessageHandler>(relaxed = true)
            val server =
                DesktopPasteServer(
                    TestReadWritePort(),
                    DesktopExceptionHandler(),
                    DesktopServerFactory(),
                    AuthenticatedWsServerModule(serverInfo, serverStore, serverSessions, serverHandler),
                )
            val httpClient = HttpClient(CIO) { install(WebSockets) }
            val connector =
                WsClientConnector(
                    appInfo = clientInfo,
                    client = httpClient,
                    secureStore = clientStore,
                    wsSessionManager = clientSessions,
                    wsMessageHandler = clientHandler,
                )

            try {
                server.start()
                connector.connectAsync("127.0.0.1", server.port(), serverInfo.appInstanceId)
                withTimeout(5.seconds) {
                    while (!clientSessions.isConnected(serverInfo.appInstanceId) ||
                        !serverSessions.isConnected(clientInfo.appInstanceId)
                    ) {
                        delay(10.milliseconds)
                    }
                }

                assertTrue(
                    clientSessions.send(
                        serverInfo.appInstanceId,
                        WsEnvelope(type = "authenticated-test", payload = "payload".encodeToByteArray()),
                    ),
                )
                coVerify(timeout = 5_000) {
                    serverHandler.handleMessage(
                        clientInfo.appInstanceId,
                        match { it.type == "authenticated-test" && it.payload.decodeToString() == "payload" },
                    )
                }
            } finally {
                clientSessions.closeAll()
                serverSessions.closeAll()
                httpClient.close()
                server.stop()
            }
        }

    @Test
    fun `loopback legacy client tolerates a malformed message`() =
        runBlocking {
            val serializer = SecureKeyPairSerializer()
            val serverKeys = CryptographyUtils.generateSecureKeyPair()
            val clientKeys = CryptographyUtils.generateSecureKeyPair()
            val serverStore = GeneralSecureStore(serverKeys, serializer, MemorySecureIO())
            val serverInfo = appInfo("legacy-server")
            val clientInfo = appInfo("legacy-client")
            serverStore.saveCryptPublicKey(
                clientInfo.appInstanceId,
                serializer.encodeCryptPublicKey(clientKeys.cryptKeyPair.publicKey),
            )
            val serverSessions = WsSessionManager()
            val serverHandler = mockk<WsMessageHandler>(relaxed = true)
            val server =
                DesktopPasteServer(
                    TestReadWritePort(),
                    DesktopExceptionHandler(),
                    DesktopServerFactory(),
                    AuthenticatedWsServerModule(serverInfo, serverStore, serverSessions, serverHandler),
                )
            val httpClient = HttpClient(CIO) { install(WebSockets) }

            try {
                server.start()
                val path =
                    "/ws/sync?appInstanceId=${clientInfo.appInstanceId}" +
                        "&targetAppInstanceId=${serverInfo.appInstanceId}"
                httpClient.webSocket(host = "127.0.0.1", port = server.port(), path = path) {
                    send(Frame.Text("not-json"))
                    WsSession(this, serverInfo.appInstanceId).sendEnvelope(
                        WsEnvelope(type = "legacy-test", payload = "payload".encodeToByteArray()),
                    )
                    coVerify(timeout = 5_000) {
                        serverHandler.handleMessage(
                            clientInfo.appInstanceId,
                            match { it.type == "legacy-test" && it.payload.decodeToString() == "payload" },
                        )
                    }
                    assertTrue(serverSessions.isConnected(clientInfo.appInstanceId))
                }
            } finally {
                serverSessions.closeAll()
                httpClient.close()
                server.stop()
            }
        }

    @Test
    fun `authentication closes cleanly when the paired key disappears`() =
        runBlocking {
            val serverInfo = appInfo("missing-key-server")
            val clientInfo = appInfo("missing-key-client")
            val serverStore = mockk<SecureStore>()
            coEvery { serverStore.existCryptPublicKey(clientInfo.appInstanceId) } returns true
            coEvery { serverStore.getMessageProcessor(clientInfo.appInstanceId) } throws IllegalStateException("gone")
            val server =
                DesktopPasteServer(
                    TestReadWritePort(),
                    DesktopExceptionHandler(),
                    DesktopServerFactory(),
                    AuthenticatedWsServerModule(
                        serverInfo,
                        serverStore,
                        WsSessionManager(),
                        mockk(relaxed = true),
                    ),
                )
            val httpClient = HttpClient(CIO) { install(WebSockets) }

            try {
                server.start()
                val path =
                    "/ws/sync?appInstanceId=${clientInfo.appInstanceId}" +
                        "&targetAppInstanceId=${serverInfo.appInstanceId}" +
                        "&authVersion=${WsAuthenticationCodec.VERSION}"
                httpClient.webSocket(host = "127.0.0.1", port = server.port(), path = path) {
                    val reason = withTimeout(5.seconds) { closeReason.await() }
                    assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
                }
            } finally {
                httpClient.close()
                server.stop()
            }
        }

    @Test
    fun `pairing v3 peers negotiate chunked payload and round-trip a large envelope`() =
        runBlocking {
            val serializer = SecureKeyPairSerializer()
            val serverKeys = CryptographyUtils.generateSecureKeyPair()
            val clientKeys = CryptographyUtils.generateSecureKeyPair()
            val serverStore = GeneralSecureStore(serverKeys, serializer, MemorySecureIO())
            val clientStore = GeneralSecureStore(clientKeys, serializer, MemorySecureIO())
            val serverInfo = appInfo("chunk-server", pairingVersion = 3)
            val clientInfo = appInfo("chunk-client", pairingVersion = 3)
            serverStore.saveCryptPublicKey(
                clientInfo.appInstanceId,
                serializer.encodeCryptPublicKey(clientKeys.cryptKeyPair.publicKey),
            )
            clientStore.saveCryptPublicKey(
                serverInfo.appInstanceId,
                serializer.encodeCryptPublicKey(serverKeys.cryptKeyPair.publicKey),
            )

            val serverSessions = WsSessionManager()
            val clientSessions = WsSessionManager()
            val serverHandler = mockk<WsMessageHandler>(relaxed = true)
            val clientHandler = mockk<WsMessageHandler>(relaxed = true)
            val server =
                DesktopPasteServer(
                    TestReadWritePort(),
                    DesktopExceptionHandler(),
                    DesktopServerFactory(),
                    AuthenticatedWsServerModule(serverInfo, serverStore, serverSessions, serverHandler),
                )
            val httpClient = HttpClient(CIO) { install(WebSockets) }
            val connector =
                WsClientConnector(
                    appInfo = clientInfo,
                    client = httpClient,
                    secureStore = clientStore,
                    wsSessionManager = clientSessions,
                    wsMessageHandler = clientHandler,
                )

            try {
                server.start()
                connector.connectAsync("127.0.0.1", server.port(), serverInfo.appInstanceId)
                withTimeout(5.seconds) {
                    while (!clientSessions.isConnected(serverInfo.appInstanceId) ||
                        !serverSessions.isConnected(clientInfo.appInstanceId)
                    ) {
                        delay(10.milliseconds)
                    }
                }

                assertTrue(clientSessions.supportsChunkedPayload(serverInfo.appInstanceId))
                assertTrue(serverSessions.supportsChunkedPayload(clientInfo.appInstanceId))
                assertTrue(
                    WsCapability.PASTE_PUSH_ACK in
                        requireNotNull(clientSessions.getSession(serverInfo.appInstanceId)).peerCapabilities,
                )
                assertTrue(
                    WsCapability.PASTE_PUSH_ACK in
                        requireNotNull(serverSessions.getSession(clientInfo.appInstanceId)).peerCapabilities,
                )

                // Larger than the server's 1 MiB receive frame limit — only
                // deliverable when actually split into chunked frames
                val payload = ByteArray(WS_PAYLOAD_CHUNK_SIZE * 2 + 12345) { (it % 251).toByte() }
                assertTrue(
                    clientSessions.send(
                        serverInfo.appInstanceId,
                        WsEnvelope(type = "chunk-test", payload = payload),
                    ),
                )
                coVerify(timeout = 10_000) {
                    serverHandler.handleMessage(
                        clientInfo.appInstanceId,
                        match { it.type == "chunk-test" && it.payload.contentEquals(payload) },
                    )
                }
            } finally {
                clientSessions.closeAll()
                serverSessions.closeAll()
                httpClient.close()
                server.stop()
            }
        }

    @Test
    fun `peers without pairing v3 stay single-frame`() =
        runBlocking {
            val serializer = SecureKeyPairSerializer()
            val serverKeys = CryptographyUtils.generateSecureKeyPair()
            val clientKeys = CryptographyUtils.generateSecureKeyPair()
            val serverStore = GeneralSecureStore(serverKeys, serializer, MemorySecureIO())
            val clientStore = GeneralSecureStore(clientKeys, serializer, MemorySecureIO())
            val serverInfo = appInfo("legacy-frame-server")
            val clientInfo = appInfo("legacy-frame-client")
            serverStore.saveCryptPublicKey(
                clientInfo.appInstanceId,
                serializer.encodeCryptPublicKey(clientKeys.cryptKeyPair.publicKey),
            )
            clientStore.saveCryptPublicKey(
                serverInfo.appInstanceId,
                serializer.encodeCryptPublicKey(serverKeys.cryptKeyPair.publicKey),
            )

            val serverSessions = WsSessionManager()
            val clientSessions = WsSessionManager()
            val server =
                DesktopPasteServer(
                    TestReadWritePort(),
                    DesktopExceptionHandler(),
                    DesktopServerFactory(),
                    AuthenticatedWsServerModule(
                        serverInfo,
                        serverStore,
                        serverSessions,
                        mockk(relaxed = true),
                    ),
                )
            val httpClient = HttpClient(CIO) { install(WebSockets) }
            val connector =
                WsClientConnector(
                    appInfo = clientInfo,
                    client = httpClient,
                    secureStore = clientStore,
                    wsSessionManager = clientSessions,
                    wsMessageHandler = mockk(relaxed = true),
                )

            try {
                server.start()
                connector.connectAsync("127.0.0.1", server.port(), serverInfo.appInstanceId)
                withTimeout(5.seconds) {
                    while (!clientSessions.isConnected(serverInfo.appInstanceId) ||
                        !serverSessions.isConnected(clientInfo.appInstanceId)
                    ) {
                        delay(10.milliseconds)
                    }
                }

                assertTrue(!clientSessions.supportsChunkedPayload(serverInfo.appInstanceId))
                assertTrue(!serverSessions.supportsChunkedPayload(clientInfo.appInstanceId))
            } finally {
                clientSessions.closeAll()
                serverSessions.closeAll()
                httpClient.close()
                server.stop()
            }
        }

    private fun appInfo(
        id: String,
        pairingVersion: Int? = null,
    ): AppInfo =
        AppInfo(
            appInstanceId = id,
            appVersion = "test",
            appRevision = "test",
            userName = id,
            pairingVersion = pairingVersion,
        )
}

private class AuthenticatedWsServerModule(
    private val appInfo: AppInfo,
    private val secureStore: SecureStore,
    private val sessionManager: WsSessionManager,
    private val messageHandler: WsMessageHandler,
) : ServerModule {
    override fun installModules(): Application.() -> Unit =
        {
            install(io.ktor.server.websocket.WebSockets) {
                maxFrameSize = WS_MAX_FRAME_SIZE
            }
            routing {
                wsRouting(appInfo, secureStore, sessionManager, messageHandler)
            }
        }
}
