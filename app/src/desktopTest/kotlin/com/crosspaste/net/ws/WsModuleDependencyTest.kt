package com.crosspaste.net.ws

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.SyncApi
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.paste.PasteboardService
import com.crosspaste.platform.Platform
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncResolver
import com.crosspaste.sync.SyncResolverApi
import com.crosspaste.sync.TokenCacheApi
import com.crosspaste.utils.ioDispatcher
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Verifies that WebSocket-related Koin singletons can be resolved without
 * circular dependency (StackOverflowError). This catches cycles like:
 * WsMessageHandler → SyncRoutingApi → SyncManager → SyncResolver → WsClientConnector → WsMessageHandler
 */
class WsModuleDependencyTest : KoinTest {

    @AfterTest
    fun tearDown() {
        GlobalContext.stopKoin()
    }

    @Test
    fun allWsComponentsResolvableWithoutCircularDependency() {
        val mockSyncManager =
            mockk<SyncManager>(relaxed = true) {
                every { realTimeSyncScope } returns CoroutineScope(ioDispatcher + SupervisorJob())
            }

        val testModule =
            module {
                // Stubs for dependencies outside the cycle
                single<AppControl> { mockk(relaxed = true) }
                single<CommonConfigManager> { mockk(relaxed = true) }
                single<AppInfo> { mockk(relaxed = true) { every { appInstanceId } returns "test" } }
                single<Platform> { mockk(relaxed = true) { every { isDesktop() } returns true } }
                single<NearbyDeviceManager> { mockk(relaxed = true) }
                single<NetworkInterfaceService> { mockk(relaxed = true) }
                single<PasteBonjourService> { mockk(relaxed = true) }
                single<PasteboardService> { mockk(relaxed = true) }
                single<RatingPromptManager> { mockk(relaxed = true) }
                single<SecureKeyPairSerializer> { mockk(relaxed = true) }
                single<SecureStore> { mockk(relaxed = true) }
                single<SyncApi> { SyncApi }
                single<SyncClientApi> { mockk(relaxed = true) }
                single<SyncDeviceManager> { mockk(relaxed = true) }
                single<SyncInfoFactory> { mockk(relaxed = true) }
                single<SyncRuntimeInfoDao> { mockk(relaxed = true) }
                single<TelnetHelper> { mockk(relaxed = true) }
                single<TokenCacheApi> { mockk(relaxed = true) }

                // The actual components under test — mirrors DesktopNetworkModule wiring
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
                single<SyncManager> { mockSyncManager }
                single<SyncRoutingApi> { get<SyncManager>() }
            }

        GlobalContext.startKoin { modules(testModule) }

        // If any of these throw StackOverflowError, we have a circular dependency
        assertNotNull(get<WsSessionManager>())
        assertNotNull(get<WsMessageHandler>())
        assertNotNull(get<WsClientConnector>())
        assertNotNull(get<SyncResolverApi>())
    }
}
