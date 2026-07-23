package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.TestAppTokenService
import com.crosspaste.config.TestAppConfig
import com.crosspaste.config.TestConfigManager
import com.crosspaste.config.TestReadWritePort
import com.crosspaste.db.secure.MemorySecureIO
import com.crosspaste.net.clientapi.PairingV3ClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.TestSyncRoutingApi
import com.crosspaste.pairing.v3.PairingAcceptanceWindow
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingRateLimiter
import com.crosspaste.pairing.v3.PairingReceiptCache
import com.crosspaste.pairing.v3.PairingSessionStore
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.pairing.v3.PairingVersionCoordinator
import com.crosspaste.pairing.v3.PakeProvider
import com.crosspaste.pairing.v3.TestPakeProvider
import com.crosspaste.platform.Platform
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.getPlatformUtils
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration

class TestInstance(
    val appInstanceId: String,
    userName: String = appInstanceId,
    pairingPinLifetime: Duration = PairingV3.DEFAULT_PIN_LIFETIME,
    pairingGenerationGrace: Duration = PairingV3.DEFAULT_GENERATION_GRACE,
    pairingRateLimiter: PairingRateLimiter = PairingRateLimiter(),
    pakeProvider: PakeProvider = TestPakeProvider(),
    pairingV3Enabled: Boolean = true,
) {
    companion object {
        val platform: Platform = getPlatformUtils().platform
        val deviceUtils: DeviceUtils = DesktopDeviceUtils(platform)
        val secureKeyPairSerializer = SecureKeyPairSerializer()
        val syncApi: SyncApi = SyncApi
        val exceptionHandler: ExceptionHandler = DesktopExceptionHandler()
        val networkInterfaceService: NetworkInterfaceService =
            TestNetworkInterfaceService(
                testNetworkInterfaces =
                    listOf(
                        NetworkInterfaceInfo("lo0", 8, "127.0.0.1"),
                    ),
                testPreferredInterface = NetworkInterfaceInfo("lo0", 8, "127.0.0.1"),
            )
    }

    val appInfo =
        AppInfo(
            appInstanceId = appInstanceId,
            appVersion = "0.0.1",
            appRevision = "1",
            userName = userName,
        )

    val secureKeyPair: SecureKeyPair = runBlocking { generateSecureKeyPair() }
    val secureIO: MemorySecureIO = MemorySecureIO()

    val secureStore: SecureStore =
        GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)

    val appTokenApi = TestAppTokenService()

    private val readWritePort = TestReadWritePort()

    private val clientEncryptPlugin = ClientEncryptPlugin(secureStore)
    private val clientDecryptPlugin = ClientDecryptPlugin(secureStore)

    val pasteClient = PasteClient(appInfo, clientEncryptPlugin, clientDecryptPlugin)

    val syncRoutingApi: TestSyncRoutingApi = TestSyncRoutingApi()

    val pairingSessionStore = PairingSessionStore()

    val pairingAcceptanceWindow = PairingAcceptanceWindow()

    val pairingReceiptCache = PairingReceiptCache()

    val pairingVersionCoordinator = PairingVersionCoordinator()

    val pairingV3ClientApi = PairingV3ClientApi(pasteClient, exceptionHandler)

    val pairingProtocolV3Service =
        PairingProtocolV3Service(
            appInfo = appInfo,
            pairingV3ClientApi = pairingV3ClientApi,
            pakeProvider = pakeProvider,
            receiptCache = pairingReceiptCache,
            rateLimiter = pairingRateLimiter,
            secureKeyPairSerializer = secureKeyPairSerializer,
            secureStore = secureStore,
            sessionStore = pairingSessionStore,
            acceptanceWindow = pairingAcceptanceWindow,
            isPairingV3Enabled = { pairingV3Enabled },
            pinLifetime = pairingPinLifetime,
            generationGrace = pairingGenerationGrace,
        )

    private val serverFactory =
        DesktopServerFactory()

    private val endpointInfoFactory =
        EndpointInfoFactory(deviceUtils, lazy { pasteServer }, platform)

    private val syncInfoFactory =
        SyncInfoFactory(appInfo, endpointInfoFactory)

    private val pendingKeyExchangeStore = PendingKeyExchangeStore()

    private val serverModule =
        TestServerModule(
            appInfo = appInfo,
            appTokenApi = appTokenApi,
            configManager = TestConfigManager(TestAppConfig()),
            exceptionHandler = exceptionHandler,
            networkInterfaceService = networkInterfaceService,
            pendingKeyExchangeStore = pendingKeyExchangeStore,
            secureKeyPairSerializer = secureKeyPairSerializer,
            secureStore = secureStore,
            serverEncryptPluginFactory = ServerEncryptPluginFactory(secureStore),
            serverDecryptionPluginFactory = ServerDecryptionPluginFactory(secureStore),
            syncApi = syncApi,
            syncInfoFactory = syncInfoFactory,
            syncRoutingApi = syncRoutingApi,
            pairingProtocolV3Service = pairingProtocolV3Service,
            pairingVersionCoordinator = pairingVersionCoordinator,
        )

    val pasteServer: Server =
        DesktopPasteServer(
            readWritePort,
            exceptionHandler,
            serverFactory,
            serverModule,
        )

    val syncClientApi =
        SyncClientApi(pasteClient, exceptionHandler, secureKeyPairSerializer, secureStore, syncApi)

    val telnetHelper = TelnetHelper(networkInterfaceService, pasteClient, syncApi, syncInfoFactory)

    suspend fun start() {
        pasteServer.start()
    }

    suspend fun stop() {
        pasteServer.stop()
        pasteClient.close()
    }

    fun getPort(): Int = pasteServer.port()

    fun getToken(): Int =
        appTokenApi.token.value
            .concatToString()
            .toInt()
}
