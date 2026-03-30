package com.crosspaste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.image.DesktopFaviconLoader
import com.crosspaste.image.FaviconLoader
import com.crosspaste.mcp.DesktopMcpServer
import com.crosspaste.mcp.McpResourceProvider
import com.crosspaste.mcp.McpServer
import com.crosspaste.mcp.McpToolProvider
import com.crosspaste.net.DesktopNetworkInterfaceService
import com.crosspaste.net.DesktopPasteBonjourService
import com.crosspaste.net.DesktopPasteServer
import com.crosspaste.net.DesktopResourcesClient
import com.crosspaste.net.DesktopServerFactory
import com.crosspaste.net.DesktopServerModule
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.ResourcesClient
import com.crosspaste.net.Server
import com.crosspaste.net.ServerFactory
import com.crosspaste.net.ServerModule
import com.crosspaste.net.SyncApi
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.ws.WsClientConnector
import com.crosspaste.net.ws.WsMessageHandler
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.sync.GeneralNearbyDeviceManager
import com.crosspaste.sync.GeneralSyncManager
import com.crosspaste.sync.MarketingNearbyDeviceManager
import com.crosspaste.sync.MarketingSyncManager
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.PendingKeyExchangeStore
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
        single<ExceptionHandler> { DesktopExceptionHandler() }
        single<FaviconLoader> { DesktopFaviconLoader(get(), get()) }
        single<McpResourceProvider> { McpResourceProvider(get(), get()) }
        single<McpToolProvider> { McpToolProvider(get(), get(), get(), get(), get(), get()) }
        single<McpServer> {
            DesktopMcpServer(
                mcpPort = (get<DesktopConfigManager>().getCurrentConfig()).mcpServerPort,
                mcpToolProvider = get(),
                mcpResourceProvider = get(),
            )
        }
        single<NearbyDeviceManager> {
            if (marketingMode) {
                MarketingNearbyDeviceManager()
            } else {
                GeneralNearbyDeviceManager(get(), get(), get(), get())
            }
        }
        single<NetworkInterfaceService> { DesktopNetworkInterfaceService(get()) }
        single<ResourcesClient> { DesktopResourcesClient(get(), get()) }
        single<PasteBonjourService> { DesktopPasteBonjourService(get(), get(), get(), get()) }
        single<PendingKeyExchangeStore> { PendingKeyExchangeStore() }
        single<PasteClient> { PasteClient(get(), get(), get()) }
        single<PullClientApi> { PullClientApi(get(), get()) }
        single<PasteClientApi> { PasteClientApi(get(), get()) }
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
        single<WsSessionManager> {
            WsSessionManager()
        }
        single<WsMessageHandler> {
            WsMessageHandler(
                lazyAppControl = lazy { get() },
                lazyPasteboardService = lazy { get() },
                lazySyncRoutingApi = lazy { get() },
                secureStore = get(),
                wsSessionManager = get(),
            )
        }
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
                pendingKeyExchangeStore = get(),
                pasteboardService = get(),
                pasteDao = get(),
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
        single<SyncClientApi> { SyncClientApi(get(), get(), get(), get(), get()) }
        single<SyncManager> {
            if (marketingMode) {
                MarketingSyncManager()
            } else {
                GeneralSyncManager(
                    syncResolver = get(),
                    syncRuntimeInfoDao = get(),
                    wsSessionManager = get(),
                )
            }
        }
        single<SyncDeviceManager> {
            SyncDeviceManager(
                secureStore = get(),
                syncClientApi = get(),
                syncRuntimeInfoDao = get(),
            )
        }
        single<SyncResolverApi> {
            SyncResolver(
                appInfo = get(),
                localPlatform = get(),
                lazyNearbyDeviceManager = lazy { get() },
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
        single<SyncRoutingApi> { get<SyncManager>() }
        single<TelnetHelper> { TelnetHelper(get(), get()) }
    }
