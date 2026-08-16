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
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.ResourcesClient
import com.crosspaste.net.Server
import com.crosspaste.net.TestNetworkInterfaceService
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.AppPathProvider
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.RenderingService
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.PastePullService
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.getPlatformUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
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
    fun testHeadlessStartupAndShutdownServicesResolve(): Unit =
        runBlocking {
            val tempRoot = createTempDirectory("crosspaste-headless-di").toOkioPath(normalize = true)
            val platform = getPlatformUtils().platform
            val appPathProvider = TempAppPathProvider(tempRoot)
            val configManager =
                DesktopConfigManager(
                    FilePersist.createOneFilePersist(appPathProvider.resolve("appConfig.json", AppFileType.USER)),
                    DesktopLocaleUtils,
                )
            val desktopModule =
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

            val testOverrides =
                module {
                    single<SecureStore> { mockk(relaxed = true) }
                    single<NetworkInterfaceService> { TestNetworkInterfaceService() }
                }
            val koinApplication =
                koinApplication {
                    allowOverride(true)
                    modules(desktopModule.modules() + testOverrides)
                }
            val koin = koinApplication.koin
            var pasteboardService: PasteboardService? = null
            var syncManager: SyncManager? = null
            var server: Server? = null
            var cliServer: CliServer? = null
            var mcpServer: McpServer? = null
            var pasteClient: PasteClient? = null
            var pasteBonjourService: PasteBonjourService? = null
            var cleanScheduler: CleanScheduler? = null
            var renderingService: RenderingService<String>? = null
            var taskExecutor: TaskExecutor? = null
            var resourcesClient: ResourcesClient? = null
            var driverFactory: DriverFactory? = null

            try {
                // The startApplication() service set on the headless path.
                assertNotNull(koin.get<DesktopConfigManager>())
                assertNotNull(koin.get<NotificationManager>())
                pasteboardService = koin.get()
                assertNotNull(pasteboardService)
                assertNotNull(koin.get<QRCodeGenerator>())
                syncManager = koin.get()
                assertNotNull(syncManager)
                assertNotNull(koin.get<PastePullService>())
                server = koin.get()
                assertNotNull(server)
                cliServer = koin.get()
                assertNotNull(cliServer)
                mcpServer = koin.get()
                assertNotNull(mcpServer)
                pasteClient = koin.get()
                assertNotNull(pasteClient)
                pasteBonjourService = koin.get()
                assertNotNull(pasteBonjourService)
                cleanScheduler = koin.get()
                assertNotNull(cleanScheduler)
                assertNotNull(koin.get<DesktopPidFileService>())
                renderingService = koin.get(named("urlRendering"))
                assertNotNull(renderingService)

                // The shutdownAllServices()/runHeadless() service set on the headless path.
                assertNotNull(koin.get<AppExitService>())
                assertNotNull(koin.get<AppLock>())
                taskExecutor = koin.get()
                assertNotNull(taskExecutor)
                assertNotNull(koin.get<UserDataPathProvider>())
                resourcesClient = koin.get()
                assertNotNull(resourcesClient)
                driverFactory = koin.get()
                assertNotNull(driverFactory)
            } finally {
                runCatching { cleanScheduler?.stop() }
                runCatching { taskExecutor?.shutdown() }
                runCatching { renderingService?.stop() }
                runCatching { pasteboardService?.stop() }
                runCatching { pasteBonjourService?.close() }
                runCatching { server?.stop() }
                runCatching { cliServer?.stop() }
                runCatching { mcpServer?.stop() }
                runCatching { syncManager?.stop() }
                runCatching { pasteClient?.close() }
                runCatching { resourcesClient?.close() }
                runCatching { driverFactory?.closeDriver() }
                koinApplication.close()
                Files.walk(tempRoot.toNioPath()).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
}
