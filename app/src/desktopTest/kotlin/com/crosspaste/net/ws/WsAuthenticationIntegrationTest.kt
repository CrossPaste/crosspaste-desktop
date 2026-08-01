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
import com.crosspaste.utils.CryptographyUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

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
                withTimeout(5_000) {
                    while (!clientSessions.isConnected(serverInfo.appInstanceId) ||
                        !serverSessions.isConnected(clientInfo.appInstanceId)
                    ) {
                        delay(10)
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

    private fun appInfo(id: String): AppInfo =
        AppInfo(
            appInstanceId = id,
            appVersion = "test",
            appRevision = "test",
            userName = id,
        )
}

private class AuthenticatedWsServerModule(
    private val appInfo: AppInfo,
    private val secureStore: GeneralSecureStore,
    private val sessionManager: WsSessionManager,
    private val messageHandler: WsMessageHandler,
) : ServerModule {
    override fun installModules(): Application.() -> Unit =
        {
            install(io.ktor.server.websocket.WebSockets)
            routing {
                wsRouting(appInfo, secureStore, sessionManager, messageHandler)
            }
        }
}
