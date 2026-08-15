package com.crosspaste.headless

import coil3.PlatformContext
import org.koin.dsl.koinApplication
import kotlin.test.Test
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
}
