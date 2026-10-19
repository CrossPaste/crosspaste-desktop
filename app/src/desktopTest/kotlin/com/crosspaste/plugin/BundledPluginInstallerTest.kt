package com.crosspaste.plugin

import com.crosspaste.app.AppFileType
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.plugin.PluginStateDao
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundledPluginInstallerTest {

    private val fileSystem = FileSystem.SYSTEM

    private val pluginsDir: Path = Files.createTempDirectory("plugins").toOkioPath()

    private val translateDir = pluginsDir.resolve("translate")

    private val installer = BundledPluginInstaller({ pluginsDir }, fileSystem)

    @AfterTest
    fun cleanup() {
        fileSystem.deleteRecursively(pluginsDir)
    }

    @Test
    fun `copies the bundled plugins once`() {
        installer.install()

        BundledPluginInstaller.BUNDLED_PLUGINS.single().files.forEach { file ->
            assertTrue(fileSystem.exists(translateDir.resolve(file)), file)
        }
        assertEquals(
            "translate",
            fileSystem.read(pluginsDir.resolve(BundledPluginInstaller.INSTALLED_MARKER)) { readUtf8() },
        )
    }

    @Test
    fun `a deleted or edited bundled plugin is left as the user made it`() {
        installer.install()
        val manifest = translateDir.resolve(PluginRepository.MANIFEST_FILE_NAME)
        fileSystem.write(manifest) { writeUtf8("edited") }

        installer.install()
        assertEquals("edited", fileSystem.read(manifest) { readUtf8() })

        fileSystem.deleteRecursively(translateDir)
        installer.install()
        assertFalse(fileSystem.exists(translateDir))
    }

    @Test
    fun `an existing folder with the same name is not overwritten`() {
        fileSystem.createDirectories(translateDir)
        fileSystem.write(translateDir.resolve("mine.txt")) { writeUtf8("x") }

        installer.install()

        assertEquals(listOf("mine.txt"), fileSystem.list(translateDir).map { it.name })
    }

    @Test
    fun `the bundled translate plugin is compatible and disabled on every desktop platform`() =
        runTest {
            installer.install()
            val userDataPathProvider =
                mockk<UserDataPathProvider> {
                    every { resolve(appFileType = AppFileType.PLUGIN) } returns pluginsDir
                }
            listOf(
                Platform(Platform.MACOS, "aarch64", 64, "15"),
                Platform(Platform.WINDOWS, "amd64", 64, "11"),
                Platform(Platform.LINUX, "x86_64", 64, "24.04"),
            ).forEach { platform ->
                val repository =
                    PluginRepository(
                        environment = PluginEnvironment(platform, "2.3.0", setOf(PluginEnvironment.RUNTIME_PROCESS)),
                        pluginStateDao = PluginStateDao(createDatabase(TestDriverFactory())),
                        userDataPathProvider = userDataPathProvider,
                        fileSystem = fileSystem,
                    )
                repository.reload()
                val plugin = repository.plugins.value.single()

                assertNull(plugin.problem, platform.name)
                assertFalse(plugin.enabled)
                assertEquals("翻译", plugin.manifest!!.displayName("zh"))
                assertEquals(
                    "English",
                    plugin.manifest!!
                        .actions
                        .first()
                        .displayName("zh"),
                )
                assertEquals(
                    "简体中文",
                    plugin.manifest!!
                        .actions
                        .first { it.id == "zh-CN" }
                        .displayName("en"),
                )
            }
        }
}
