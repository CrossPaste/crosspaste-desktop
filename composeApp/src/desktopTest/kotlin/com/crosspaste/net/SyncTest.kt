package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.AppTokenService
import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.config.TestReadWritePort
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.realm.MemorySecureIO
import com.crosspaste.realm.secure.SecureIO
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
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
import kotlin.test.assertTrue

class SyncTest : KoinTest {

    private val appTokenApi by inject<AppTokenApi>()

    private val pasteServer by inject<PasteServer<*, *>>()

    private val readWritePort by inject<ReadWriteConfig<Int>>(named("readWritePort"))

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

        private val serverSecureIO = MemorySecureIO()

        private val clientSecureIO = MemorySecureIO()

        private val testModule =
            module {
                // simple component
                single<ReadWriteConfig<Int>>(named("readWritePort")) { TestReadWritePort() }

                // net component
                single<PasteClient> { PasteClient(clientAppInfo, get(), get()) }
                single<PasteServer<*, *>> {
                    PasteServer(
                        get(named("readWritePort")),
                        get<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>>(),
                        get(),
                    )
                }
                single<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>> {
                    DesktopServerFactory()
                }
                single<ServerModule> {
                    TestServerModule(
                        get(),
                        get(),
                        get(named("serverSecureStore")),
                        get(),
                        get(),
                    )
                }
                single<SyncClientApi> { SyncClientApi(get(), get(), get(named("clientSecureStore"))) }

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
                single<SecureIO>(named("serverSecureIO")) { serverSecureIO }
                single<SecureIO>(named("clientSecureIO")) { clientSecureIO }

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
    fun testSync() {
        pasteServer.start()

        runBlocking {
            syncClientApi.trust(
                serverAppInfo.appInstanceId,
                appTokenApi.token.value.concatToString().toInt(),
            ) {
                buildUrl(host, readWritePort.getValue())
            }
        }

        assertTrue(serverSecureIO.existCryptPublicKey(clientAppInfo.appInstanceId))
        assertTrue(clientSecureIO.existCryptPublicKey(serverAppInfo.appInstanceId))
    }
}
