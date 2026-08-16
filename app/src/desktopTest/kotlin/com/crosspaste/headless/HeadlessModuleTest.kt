package com.crosspaste.headless

import coil3.PlatformContext
import coil3.memory.MemoryCache
import com.crosspaste.app.AppEnv
import com.crosspaste.desktopAppModule
import com.crosspaste.pairing.v3.PairingCapabilityFlag
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class HeadlessModuleTest {

    /**
     * The coil ImageLoader/MemoryCache singles in desktopAppModule resolve
     * PlatformContext, which uiModule provides for the GUI assembly. The headless
     * assembly must provide it too, or any lazy resolution of those singles
     * (favicon/app-source/thumbnail loading) crashes with NoDefinitionFoundException.
     */
    @Test
    fun testHeadlessUiModuleProvidesPlatformContext() {
        val koin = koinApplication { modules(headlessUiModule()) }.koin
        assertSame(PlatformContext.INSTANCE, koin.get<PlatformContext>())
    }

    /**
     * Regression pin across modules: resolving the MemoryCache single defined in
     * desktopAppModule pulls PlatformContext at build time, which is exactly the
     * lazy path that crashed under the headless assembly before headlessUiModule
     * bound PlatformContext.
     */
    @Test
    fun testMemoryCacheResolvesUnderHeadlessAssembly() {
        val koin =
            koinApplication {
                modules(
                    desktopAppModule(
                        appEnv = AppEnv.TEST,
                        appMetadataRepository = mockk(relaxed = true),
                        appPathProvider = mockk(relaxed = true),
                        configManager = mockk(relaxed = true),
                        crossPasteLogger = mockk(relaxed = true),
                        deviceUtils = mockk(relaxed = true),
                        klogger = KotlinLogging.logger {},
                        platform = mockk(relaxed = true),
                        pairingCapabilityFlag = PairingCapabilityFlag(2),
                    ),
                    headlessUiModule(),
                )
            }.koin
        assertNotNull(koin.get<MemoryCache>())
    }
}
