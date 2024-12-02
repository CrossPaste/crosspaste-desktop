package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.config.TestReadWritePort
import com.crosspaste.dto.sync.SyncInfo
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
import com.crosspaste.realm.MemorySecureIO
import com.crosspaste.realm.secure.SecureIO
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.buildUrl
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SyncTest : KoinTest {

    private val appTokenApi by inject<AppTokenApi>()

    private val endpointInfoFactory by inject<EndpointInfoFactory>()

    private val pasteServer by inject<Server>()

    private val readWritePort by inject<ReadWriteConfig<Int>>(named("readWritePort"))

    private val secureKeyPairSerializer by inject<SecureKeyPairSerializer>()

    private val serverSecureIO by inject<SecureIO>(named("serverSecureIO"))

    private val clientSecureIO by inject<SecureIO>(named("clientSecureIO"))

    private val syncClientApi by inject<SyncClientApi>()

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

        private val serverSecureKeyPair = generateSecureKeyPair()

        private val clientSecureKeyPair = generateSecureKeyPair()

        private val testModule =
            module {
                // simple component
                single<AppInfo>(named("serverAppInfo")) { serverAppInfo }
                single<AppInfo>(named("clientAppInfo")) { clientAppInfo }
                single<DeviceUtils> { DesktopDeviceUtils }
                single<EndpointInfoFactory> { EndpointInfoFactory(get(), lazy { get<Server>() }) }
                single<ReadWriteConfig<Int>>(named("readWritePort")) { TestReadWritePort() }

                // net component
                single<ExceptionHandler> { DesktopExceptionHandler() }
                single<PasteClient> { PasteClient(get(named("clientAppInfo")), get(), get()) }
                single<Server> {
                    DesktopPasteServer(
                        get(named("readWritePort")),
                        get(),
                        get<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>>(),
                        get(),
                    )
                }
                single<SyncApi> { SyncApi }
                single<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>> {
                    DesktopServerFactory()
                }
                single<ServerModule> {
                    TestServerModule(
                        get(named("serverAppInfo")),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(named("serverSecureStore")),
                        get(),
                        get(),
                        get(),
                        get(),
                    )
                }
                single<SyncClientApi> { SyncClientApi(get(), get(), get(), get(named("clientSecureStore"))) }
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
                single<AppTokenApi> { AppTokenService() }
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
        pasteServer.start()

        var result =
            runBlocking {
                syncClientApi.trust(
                    serverAppInfo.appInstanceId,
                    appTokenApi.token.value.concatToString().toInt(),
                ) {
                    buildUrl("localhost", readWritePort.getValue())
                }
            }

        assertTrue(result is SuccessResult)

        assertTrue(serverSecureIO.existCryptPublicKey(clientAppInfo.appInstanceId))
        assertTrue(clientSecureIO.existCryptPublicKey(serverAppInfo.appInstanceId))
        assertContentEquals(
            serverSecureIO.serializedPublicKey(clientAppInfo.appInstanceId),
            secureKeyPairSerializer.encodeCryptPublicKey(clientSecureKeyPair.cryptKeyPair.publicKey),
        )
        assertContentEquals(
            clientSecureIO.serializedPublicKey(serverAppInfo.appInstanceId),
            secureKeyPairSerializer.encodeCryptPublicKey(serverSecureKeyPair.cryptKeyPair.publicKey),
        )

        result =
            runBlocking {
                syncClientApi.heartbeat(targetAppInstanceId = serverAppInfo.appInstanceId) {
                    buildUrl("localhost", readWritePort.getValue())
                }
            }

        assertTrue(result is SuccessResult)

        val syncInfo =
            SyncInfo(
                appInfo = clientAppInfo,
                endpointInfo = endpointInfoFactory.createEndpointInfo(),
            )

        result =
            runBlocking {
                syncClientApi.heartbeat(
                    syncInfo = syncInfo,
                    targetAppInstanceId = serverAppInfo.appInstanceId,
                ) {
                    buildUrl("localhost", readWritePort.getValue())
                }
            }

        assertTrue(result is SuccessResult)

        pasteServer.stop()
    }
}
