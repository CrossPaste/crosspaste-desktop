package com.crosspaste.headless

import com.crosspaste.DesktopModule
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppLock
import com.crosspaste.app.DesktopPidFileService
import com.crosspaste.clean.CleanScheduler
import com.crosspaste.cli.CliServer
import com.crosspaste.config.AppMetadataRepository
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.mcp.McpServer
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.ResourcesClient
import com.crosspaste.net.Server
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.AppPathProvider
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.RenderingService
import com.crosspaste.sync.PastePullService
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.getPlatformUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Resolves every service the headless startup and shutdown paths in
 * [com.crosspaste.CrossPaste] touch against the complete headless Koin
 * assembly ([DesktopModule.modules] with headless = true).
 *
 * This is the regression pin for definition gaps that only surface at
 * runtime on a server: a GUI-only binding referenced from a service that
 * headless mode starts (e.g. DesktopAppUpdateService requiring the
 * uiModule-only DesktopAppWindowManager) crashes the daemon with
 * NoDefinitionFoundException after it has already written its pid file.
 */
class HeadlessStartupResolutionTest {

    private class TempAppPathProvider(
        root: Path,
    ) : AppPathProvider {
        override val userHome: Path = root
        override val pasteAppPath: Path = root
        override val pasteAppJarPath: Path = root
        override val pasteAppExePath: Path = root
        override val pasteUserPath: Path = root

        private val pathProvider = DesktopPathProvider(pasteAppPath, pasteUserPath)

        override fun resolve(
            fileName: String?,
            appFileType: AppFileType,
        ): Path = pathProvider.resolve(fileName, appFileType)
    }

    @Test
    fun testHeadlessStartupAndShutdownServicesResolve() {
        val tempRoot = createTempDirectory("crosspaste-headless-di").toOkioPath(normalize = true)
        val platform = getPlatformUtils().platform
        val appPathProvider = TempAppPathProvider(tempRoot)
        val configManager =
            DesktopConfigManager(
                FilePersist.createOneFilePersist(appPathProvider.resolve("appConfig.json", AppFileType.USER)),
                DesktopLocaleUtils,
            )
        val module =
            DesktopModule(
                appEnv = AppEnv.TEST,
                appMetadataRepository =
                    AppMetadataRepository(
                        FilePersist.createOneFilePersist(appPathProvider.resolve(".metadata", AppFileType.USER)),
                        DesktopDeviceUtils(platform),
                    ),
                appPathProvider = appPathProvider,
                configManager = configManager,
                crossPasteLogger = mockk<CrossPasteLogger>(relaxed = true),
                deviceUtils = DesktopDeviceUtils(platform),
                klogger = KotlinLogging.logger {},
                platform = platform,
                headless = true,
            )

        val koin = koinApplication { modules(module.modules()) }.koin

        // The startApplication() service set on the headless path.
        assertNotNull(koin.get<DesktopConfigManager>())
        assertNotNull(koin.get<NotificationManager>())
        assertNotNull(koin.get<PasteboardService>())
        assertNotNull(koin.get<QRCodeGenerator>())
        assertNotNull(koin.get<SyncManager>())
        assertNotNull(koin.get<PastePullService>())
        assertNotNull(koin.get<Server>())
        assertNotNull(koin.get<CliServer>())
        assertNotNull(koin.get<McpServer>())
        assertNotNull(koin.get<PasteClient>())
        assertNotNull(koin.get<PasteBonjourService>())
        assertNotNull(koin.get<CleanScheduler>())
        assertNotNull(koin.get<DesktopPidFileService>())
        assertNotNull(koin.get<RenderingService<String>>(named("urlRendering")))

        // The shutdownAllServices()/runHeadless() service set on the headless path.
        assertNotNull(koin.get<AppExitService>())
        assertNotNull(koin.get<AppLock>())
        assertNotNull(koin.get<TaskExecutor>())
        assertNotNull(koin.get<UserDataPathProvider>())
        assertNotNull(koin.get<ResourcesClient>())
        assertNotNull(koin.get<DriverFactory>())
    }
}
