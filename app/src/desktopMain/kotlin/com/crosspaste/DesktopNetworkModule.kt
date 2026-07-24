package com.crosspaste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.image.DesktopFaviconLoader
import com.crosspaste.image.FaviconLoader
import com.crosspaste.mcp.DesktopMcpServer
import com.crosspaste.mcp.McpResourceProvider
import com.crosspaste.mcp.McpServer
import com.crosspaste.mcp.McpToolProvider
import com.crosspaste.net.DesktopNetworkInterfaceService
import com.crosspaste.net.DesktopNetworkProfileService
import com.crosspaste.net.DesktopPasteBonjourService
import com.crosspaste.net.DesktopPasteServer
import com.crosspaste.net.DesktopResourcesClient
import com.crosspaste.net.DesktopServerFactory
import com.crosspaste.net.DesktopServerModule
import com.crosspaste.net.LinuxNetworkStateMonitor
import com.crosspaste.net.MacosNetworkStateMonitor
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.NetworkProfileService
import com.crosspaste.net.NetworkStateMonitor
import com.crosspaste.net.NoopNetworkStateMonitor
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.ResourcesClient
import com.crosspaste.net.Server
import com.crosspaste.net.ServerFactory
import com.crosspaste.net.ServerModule
import com.crosspaste.net.SyncApi
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.WindowsNetworkStateMonitor
import com.crosspaste.net.clientapi.PairingV3ClientApi
import com.crosspaste.net.clientapi.PairingV3Transport
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.PushClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.ws.WsClientConnector
import com.crosspaste.net.ws.WsMessageHandler
import com.crosspaste.net.ws.WsPendingRequests
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.pairing.v3.PairingAcceptanceWindow
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingRateLimiter
import com.crosspaste.pairing.v3.PairingReceiptCache
import com.crosspaste.pairing.v3.PairingSessionStore
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.pairing.v3.PairingVersionCoordinator
import com.crosspaste.pairing.v3.PakeProvider
import com.crosspaste.pairing.v3.UnavailablePakeProvider
import com.crosspaste.platform.Platform
import com.crosspaste.sync.FilePushService
import com.crosspaste.sync.GeneralNearbyDeviceManager
import com.crosspaste.sync.GeneralSyncManager
import com.crosspaste.sync.MarketingNearbyDeviceManager
import com.crosspaste.sync.MarketingSyncManager
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.sync.SharePushOrchestrator
import com.crosspaste.sync.SyncDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncResolver
import com.crosspaste.sync.SyncResolverApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.netty.NettyApplicationEngine
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun desktopNetworkModule(marketingMode: Boolean): Module =
    module {
        // region MCP
        single<McpResourceProvider> { McpResourceProvider(get(), get()) }
        single<McpServer> {
            DesktopMcpServer(
                mcpPort = (get<DesktopConfigManager>().getCurrentConfig()).mcpServerPort,
                mcpToolProvider = get(),
                mcpResourceProvider = get(),
            )
        }
        single<McpToolProvider> { McpToolProvider(get(), get(), get(), get(), get(), get()) }
        // endregion

        // region Network core
        single<ExceptionHandler> { DesktopExceptionHandler() }
        single<NetworkInterfaceService> { DesktopNetworkInterfaceService(get(), get()) }
        single<NetworkProfileService> { DesktopNetworkProfileService(get(), get(), get(), get()) }
        single<NetworkStateMonitor> {
            val platform = get<Platform>()
            when {
                platform.isMacos() -> MacosNetworkStateMonitor()
                platform.isWindows() -> WindowsNetworkStateMonitor()
                platform.isLinux() -> LinuxNetworkStateMonitor()
                else -> NoopNetworkStateMonitor()
            }
        }
        single<PasteBonjourService> { DesktopPasteBonjourService(get(), get(), get(), get()) }
        single<TelnetHelper> { TelnetHelper(get(), get(), get(), get()) }
        // endregion

        // region HTTP client & API
        single<FaviconLoader> { DesktopFaviconLoader(get(), get()) }
        single<PairingV3ClientApi> { PairingV3ClientApi(get(), get()) }
        single<PairingV3Transport> { get<PairingV3ClientApi>() }
        single<PasteClient> { PasteClient(get(), get(), get()) }
        single<PasteClientApi> { PasteClientApi(get(), get()) }
        single<PullClientApi> { PullClientApi(get(), get(), get()) }
        single<PushClientApi> { PushClientApi(get(), get(), get()) }
        single<ResourcesClient> { DesktopResourcesClient(get(), get()) }
        single<SyncClientApi> { SyncClientApi(get(), get(), get(), get(), get()) }
        // endregion

        // region Server
        single<Server> {
            DesktopPasteServer(
                get(named("readWritePort")),
                get(),
                get<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>>(),
                get(),
            )
        }
        single<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>> {
            DesktopServerFactory()
        }
        single<ServerModule> {
            DesktopServerModule(
                appControl = get(),
                appInfo = get(),
                appTokenApi = get(),
                cacheManager = get(),
                configManager = get(),
                exceptionHandler = get(),
                nearbyDeviceManager = get(),
                networkInterfaceService = get(),
                pairingProtocolV3Service = get(),
                pairingVersionCoordinator = get(),
                pendingKeyExchangeStore = get(),
                pasteboardService = get(),
                pasteDao = get(),
                pastePullService = get(),
                pasteReleaseService = get(),
                pushSessionManager = get(),
                secureKeyPairSerializer = get(),
                secureStore = get(),
                syncApi = get(),
                syncInfoFactory = get(),
                syncRoutingApi = get(),
                serverEncryptPluginFactory = get(),
                serverDecryptionPluginFactory = get(),
                userDataPathProvider = get(),
                wsMessageHandler = get(),
                wsSessionManager = get(),
            )
        }
        single<SyncApi> { SyncApi }
        single<SyncRoutingApi> { get<SyncManager>() }
        // endregion

        // region Pairing v3
        single<PairingAcceptanceWindow> { PairingAcceptanceWindow() }
        single<PairingProtocolV3Service> {
            PairingProtocolV3Service(
                appInfo = get(),
                pairingV3ClientApi = get(),
                pakeProvider = get(),
                receiptCache = get(),
                rateLimiter = get(),
                secureKeyPairSerializer = get(),
                secureStore = get(),
                sessionStore = get(),
                acceptanceWindow = get(),
                isPairingV3Enabled = { SyncApi.PAIRING_VERSION >= PairingV3.PROTOCOL_VERSION },
            )
        }
        single<PairingRateLimiter> { PairingRateLimiter() }
        single<PairingReceiptCache> { PairingReceiptCache() }
        single<PairingSessionStore> { PairingSessionStore() }
        single<PairingVersionCoordinator> { PairingVersionCoordinator() }
        // BouncyCastle's P-256 point formulas are not guaranteed constant-time
        // for every input. Fail closed until a reviewed production backend lands.
        single<PakeProvider> { UnavailablePakeProvider }
        // endregion

        // region WebSocket
        single<WsClientConnector> {
            val wsHttpClient =
                HttpClient(CIO) {
                    install(WebSockets) {
                        pingIntervalMillis = 30_000
                    }
                }
            WsClientConnector(
                appInfo = get(),
                client = wsHttpClient,
                wsSessionManager = get(),
                wsMessageHandler = get(),
            )
        }
        single<WsMessageHandler> {
            WsMessageHandler(
                lazyAppControl = lazy { get() },
                lazyCacheManager = lazy { get() },
                lazyPasteDao = lazy { get() },
                lazyPasteboardService = lazy { get() },
                lazySyncRoutingApi = lazy { get() },
                secureStore = get(),
                userDataPathProvider = get(),
                wsPendingRequests = get(),
                wsSessionManager = get(),
            )
        }
        single<WsPendingRequests> {
            WsPendingRequests()
        }
        single<WsSessionManager> {
            WsSessionManager()
        }
        // endregion

        // region Sync & devices
        single<FilePushService> {
            FilePushService(
                pasteSyncProcessManager = get(),
                pushClientApi = get(),
                userDataPathProvider = get(),
            )
        }
        single<NearbyDeviceManager> {
            if (marketingMode) {
                MarketingNearbyDeviceManager()
            } else {
                GeneralNearbyDeviceManager(get(), get(), get(), get())
            }
        }
        single<PendingKeyExchangeStore> { PendingKeyExchangeStore() }
        single<PushSessionManager> {
            PushSessionManager(
                pasteDao = get(),
                pasteboardService = get(),
            )
        }
        single<SharePushOrchestrator> { SharePushOrchestrator(get(), get(), get()) }
        single<SyncDeviceManager> {
            SyncDeviceManager(
                secureStore = get(),
                syncClientApi = get(),
                syncRuntimeInfoDao = get(),
                wsSessionManager = get(),
            )
        }
        single<SyncManager> {
            if (marketingMode) {
                MarketingSyncManager()
            } else {
                GeneralSyncManager(
                    syncResolver = get(),
                    syncRuntimeInfoDao = get(),
                    syncClientApi = get(),
                    wsSessionManager = get(),
                )
            }
        }
        single<SyncResolverApi> {
            SyncResolver(
                appInfo = get(),
                localPlatform = get(),
                lazyPasteBonjourService = lazy { get() },
                networkInterfaceService = get(),
                ratingPromptManager = get(),
                secureKeyPairSerializer = get(),
                secureStore = get(),
                syncClientApi = get(),
                syncDeviceManager = get(),
                syncInfoFactory = get(),
                syncRuntimeInfoDao = get(),
                telnetHelper = get(),
                tokenCache = get(),
                wsClientConnector = get(),
                wsSessionManager = get(),
            )
        }
        // endregion
    }
