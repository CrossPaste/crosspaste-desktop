package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.TestAppTokenService
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.config.TestAppConfig
import com.crosspaste.config.TestConfigManager
import com.crosspaste.config.TestReadWritePort
import com.crosspaste.db.secure.MemorySecureIO
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.routing.TestSyncRoutingApi
import com.crosspaste.platform.Platform
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getPlatformUtils
import io.ktor.server.netty.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SyncTest : KoinTest {

    private val appTokenApi by inject<AppTokenApi>()

    private val endpointInfoFactory by inject<EndpointInfoFactory>()

    private val pasteServer by inject<Server>()

    private val readWritePort by inject<ReadWriteConfig<Int>>(named("readWritePort"))

    private val secureKeyPairSerializer by inject<SecureKeyPairSerializer>()

    private val serverSecureIO by inject<SecureIO>(named("serverSecureIO"))

    private val clientSecureIO by inject<SecureIO>(named("clientSecureIO"))

    private val syncClientApi by inject<SyncClientApi>()

    private val pasteClient by inject<PasteClient>()

    companion object {

        private val serverAppInfo =
            AppInfo(
                appInstanceId = "server-id",
                appVersion = "0.0.1",
                appRevision = "1",
                userName = "server-user",
            )

        private val clientAppInfo =
            AppInfo(
                appInstanceId = "client-id",
                appVersion = "0.0.1",
                appRevision = "1",
                userName = "client-user",
            )

        private val serverSecureKeyPair = runBlocking { generateSecureKeyPair() }

        private val clientSecureKeyPair = runBlocking { generateSecureKeyPair() }

        private val platform = getPlatformUtils().platform

        private val testModule =
            module {
                // simple component
                single<AppInfo>(named("serverAppInfo")) { serverAppInfo }
                single<AppInfo>(named("clientAppInfo")) { clientAppInfo }
                single<DeviceUtils> { DesktopDeviceUtils(platform) }
                single<EndpointInfoFactory> { EndpointInfoFactory(get(), lazy { get<Server>() }, get()) }
                single<ReadWriteConfig<Int>>(named("readWritePort")) { TestReadWritePort() }

                // net component
                single<ExceptionHandler> { DesktopExceptionHandler() }
                single<NetworkInterfaceService> {
                    TestNetworkInterfaceService(
                        testNetworkInterfaces =
                            listOf(
                                NetworkInterfaceInfo("eth0", 24, "192.168.1.100"),
                                NetworkInterfaceInfo("wlan0", 24, "192.168.1.101"),
                            ),
                        testPreferredInterface = NetworkInterfaceInfo("eth0", 24, "192.168.1.100"),
                    )
                }
                single<PasteClient> { PasteClient(get(named("clientAppInfo")), get(), get()) }
                single<Platform> { platform }
                single<Server> {
                    DesktopPasteServer(
                        get(named("readWritePort")),
                        get(),
                        get<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>>(),
                        get(),
                    )
                }
                single<CommonConfigManager> { TestConfigManager(TestAppConfig()) }
                single<PendingKeyExchangeStore> { PendingKeyExchangeStore() }
                single<SyncApi> { SyncApi }
                single<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>> {
                    DesktopServerFactory()
                }
                single<SyncInfoFactory> { SyncInfoFactory(get(named("serverAppInfo")), get()) }
                single<ServerModule> {
                    TestServerModule(
                        appInfo = get(named("serverAppInfo")),
                        appTokenApi = get(),
                        configManager = get(),
                        exceptionHandler = get(),
                        networkInterfaceService = get(),
                        pendingKeyExchangeStore = get(),
                        secureKeyPairSerializer = get(),
                        secureStore = get(named("serverSecureStore")),
                        serverEncryptPluginFactory = get(),
                        serverDecryptionPluginFactory = get(),
                        syncApi = get(),
                        syncInfoFactory = get(),
                        syncRoutingApi = get(),
                    )
                }
                single<SyncClientApi> { SyncClientApi(get(), get(), get(), get(named("clientSecureStore")), get()) }
                single<SyncRoutingApi> { TestSyncRoutingApi() }

                // secure component
                single<ClientDecryptPlugin> { ClientDecryptPlugin(get(named("clientSecureStore"))) }
                single<ClientEncryptPlugin> { ClientEncryptPlugin(get(named("clientSecureStore"))) }
                single<SecureKeyPairSerializer> { SecureKeyPairSerializer() }
                single<SecureStore>(named("serverSecureStore")) {
                    GeneralSecureStore(serverSecureKeyPair, get(), get(named("serverSecureIO")))
                }
                single<SecureStore>(named("clientSecureStore")) {
                    GeneralSecureStore(clientSecureKeyPair, get(), get(named("clientSecureIO")))
                }
                single<ServerDecryptionPluginFactory> { ServerDecryptionPluginFactory(get(named("serverSecureStore"))) }
                single<ServerEncryptPluginFactory> { ServerEncryptPluginFactory(get(named("serverSecureStore"))) }

                // io component
                single<SecureIO>(named("serverSecureIO")) { MemorySecureIO() }
                single<SecureIO>(named("clientSecureIO")) { MemorySecureIO() }

                // ui component
                single<AppTokenApi> { TestAppTokenService() }
            }
    }

    @BeforeTest
    fun setUp() {
        GlobalContext.startKoin {
            modules(testModule)
        }
    }

    @AfterTest
    fun tearDown() {
        GlobalContext.stopKoin()
    }

    @Test
    fun testTrustAndHeartbeat() {
        var result =
            runBlocking {
                pasteServer.start()

                syncClientApi.trust(
                    serverAppInfo.appInstanceId,
                    "localhost",
                    appTokenApi.token.value
                        .concatToString()
                        .toInt(),
                ) {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            }

        assertTrue(result is SuccessResult)

        assertTrue(
            runBlocking {
                serverSecureIO.existCryptPublicKey(clientAppInfo.appInstanceId)
            },
        )
        assertTrue(
            runBlocking {
                clientSecureIO.existCryptPublicKey(serverAppInfo.appInstanceId)
            },
        )
        assertContentEquals(
            runBlocking {
                serverSecureIO.serializedPublicKey(clientAppInfo.appInstanceId)
            },
            runBlocking {
                secureKeyPairSerializer.encodeCryptPublicKey(clientSecureKeyPair.cryptKeyPair.publicKey)
            },
        )
        assertContentEquals(
            runBlocking {
                clientSecureIO.serializedPublicKey(serverAppInfo.appInstanceId)
            },
            runBlocking {
                secureKeyPairSerializer.encodeCryptPublicKey(serverSecureKeyPair.cryptKeyPair.publicKey)
            },
        )

        result =
            runBlocking {
                syncClientApi.heartbeat(targetAppInstanceId = serverAppInfo.appInstanceId) {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            }

        assertTrue(result is SuccessResult)

        result =
            runBlocking {
                val syncInfo =
                    SyncInfo(
                        appInfo = clientAppInfo,
                        endpointInfo =
                            endpointInfoFactory.createEndpointInfo(
                                listOf(
                                    HostInfo(
                                        hostAddress = "localhost",
                                        networkPrefixLength = 24,
                                    ),
                                ),
                            ),
                    )

                syncClientApi.heartbeat(
                    syncInfo = syncInfo,
                    targetAppInstanceId = serverAppInfo.appInstanceId,
                ) {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            }

        assertTrue(result is SuccessResult)

        runBlocking { pasteServer.stop() }
    }

    private suspend fun exchangeAndAwaitPending(): KeyExchangeResponse {
        val exchangeResult =
            syncClientApi.exchangeKeys(serverAppInfo.appInstanceId) {
                buildUrl(HostAndPort("localhost", readWritePort.getValue()))
            }
        assertTrue(exchangeResult is SuccessResult)
        val response = (exchangeResult as SuccessResult).getResult<KeyExchangeResponse>()
        assertNotNull(response)

        // The exchange parks one token-refresh count and one pending verifier
        // on the responder (both updated asynchronously).
        withTimeout(5.seconds) {
            appTokenApi.refresh.first { it }
            appTokenApi.pendingVerifiers.first { clientAppInfo.appInstanceId in it }
        }
        return response
    }

    @Test
    fun testV2ExchangeCancelReleasesPendingExchange() {
        runBlocking {
            pasteServer.start()

            val response = exchangeAndAwaitPending()

            val cancelResult =
                syncClientApi.trustV2Cancel(response.timestamp) {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            assertTrue(cancelResult is SuccessResult)

            // Cancel funnels through the unified release path: the verifier is
            // removed and the refresh count drains back to zero (#4684).
            withTimeout(5.seconds) {
                appTokenApi.refresh.first { !it }
                appTokenApi.pendingVerifiers.first { it.isEmpty() }
            }

            // The pending exchange was consumed, so a late confirm cannot match.
            val confirmResult =
                syncClientApi.trustV2Confirm(serverAppInfo.appInstanceId, "localhost") {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            assertTrue(confirmResult is FailureResult)

            pasteServer.stop()
        }
    }

    @Test
    fun testV2StaleCancelDoesNotReleaseNewerExchange() {
        runBlocking {
            pasteServer.start()

            val firstResponse = exchangeAndAwaitPending()

            // A second exchange supersedes the first entry; the generation
            // marker is the responder's epoch-millisecond clock, so a short
            // delay guarantees it differs from the stale one.
            delay(10.milliseconds)
            val secondResponse = exchangeAndAwaitPending()
            assertTrue(secondResponse.timestamp != firstResponse.timestamp)

            // The stale cancel names the superseded exchange: the responder
            // must keep the current one alive.
            val staleCancel =
                syncClientApi.trustV2Cancel(firstResponse.timestamp) {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            assertTrue(staleCancel is SuccessResult)
            delay(200.milliseconds)
            assertTrue(appTokenApi.refresh.value)
            assertTrue(clientAppInfo.appInstanceId in appTokenApi.pendingVerifiers.value)

            // Cancelling the current generation releases it.
            val currentCancel =
                syncClientApi.trustV2Cancel(secondResponse.timestamp) {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                }
            assertTrue(currentCancel is SuccessResult)
            withTimeout(5.seconds) {
                appTokenApi.refresh.first { !it }
                appTokenApi.pendingVerifiers.first { it.isEmpty() }
            }

            pasteServer.stop()
        }
    }

    @Test
    fun testV2CancelWithoutGenerationHeaderKeepsLegacySemantics() {
        runBlocking {
            pasteServer.start()

            exchangeAndAwaitPending()

            // Callers without the generation header (older clients, the browser
            // extension) keep the original unconditional-release behaviour.
            pasteClient.post(
                "",
                typeInfo<String>(),
                urlBuilder = {
                    buildUrl(HostAndPort("localhost", readWritePort.getValue()))
                    buildUrl("sync", "trust", "v2", "cancel")
                },
            )

            withTimeout(5.seconds) {
                appTokenApi.refresh.first { !it }
                appTokenApi.pendingVerifiers.first { it.isEmpty() }
            }

            pasteServer.stop()
        }
    }
}
