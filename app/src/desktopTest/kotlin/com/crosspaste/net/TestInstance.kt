package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.TestAppTokenService
import com.crosspaste.config.TestReadWritePort
import com.crosspaste.db.secure.MemorySecureIO
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.TestSyncRoutingApi
import com.crosspaste.platform.DesktopPlatformProvider
import com.crosspaste.platform.Platform
import com.crosspaste.secure.GeneralSecureStore
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DeviceUtils

class TestInstance(
    val appInstanceId: String,
    userName: String = appInstanceId,
) {
    companion object {
        val platform: Platform = DesktopPlatformProvider().getPlatform()
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

    val secureKeyPair: SecureKeyPair = generateSecureKeyPair()
    val secureIO: MemorySecureIO = MemorySecureIO()

    val secureStore: SecureStore =
        GeneralSecureStore(secureKeyPair, secureKeyPairSerializer, secureIO)

    val appTokenApi = TestAppTokenService()

    private val readWritePort = TestReadWritePort()

    private val clientEncryptPlugin = ClientEncryptPlugin(secureStore)
    private val clientDecryptPlugin = ClientDecryptPlugin(secureStore)

    val pasteClient = PasteClient(appInfo, clientEncryptPlugin, clientDecryptPlugin)

    val syncRoutingApi: TestSyncRoutingApi = TestSyncRoutingApi()

    private val serverFactory =
        DesktopServerFactory()

    private val endpointInfoFactory =
        EndpointInfoFactory(deviceUtils, lazy { pasteServer }, platform)

    private val syncInfoFactory =
        SyncInfoFactory(appInfo, endpointInfoFactory)

    private val serverModule =
        TestServerModule(
            appInfo = appInfo,
            appTokenApi = appTokenApi,
            exceptionHandler = exceptionHandler,
            networkInterfaceService = networkInterfaceService,
            secureKeyPairSerializer = secureKeyPairSerializer,
            secureStore = secureStore,
            serverEncryptPluginFactory = ServerEncryptPluginFactory(secureStore),
            serverDecryptionPluginFactory = ServerDecryptionPluginFactory(secureStore),
            syncApi = syncApi,
            syncInfoFactory = syncInfoFactory,
            syncRoutingApi = syncRoutingApi,
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

    val telnetHelper = TelnetHelper(pasteClient, syncApi)

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
