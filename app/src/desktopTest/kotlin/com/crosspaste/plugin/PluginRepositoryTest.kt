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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginRepositoryTest {

    private val fileSystem = FileSystem.SYSTEM

    private val pluginsDir: Path = Files.createTempDirectory("plugins").toOkioPath()

    private val pluginStateDao = PluginStateDao(createDatabase(TestDriverFactory()))

    private val userDataPathProvider =
        mockk<UserDataPathProvider> {
            every { resolve(appFileType = AppFileType.PLUGIN) } returns pluginsDir
        }

    private val environment =
        PluginEnvironment(
            platform = Platform(Platform.MACOS, "aarch64", 64, "15"),
            appVersion = "2.3.0",
            runtimes = setOf(PluginEnvironment.RUNTIME_PROCESS),
        )

    private fun repository(missing: (List<String>) -> List<String> = { emptyList() }) =
        PluginRepository(environment, pluginStateDao, userDataPathProvider, fileSystem, missing)

    @AfterTest
    fun cleanup() {
        fileSystem.deleteRecursively(pluginsDir)
    }

    private fun writePlugin(
        dirName: String,
        id: String = "com.example.$dirName",
        version: String = "1.0.0",
        requires: List<String> = emptyList(),
        platformKey: String = "macos",
    ) {
        val dir = pluginsDir.resolve(dirName)
        fileSystem.createDirectories(dir)
        val requiresJson = requires.joinToString(",") { "\"$it\"" }
        fileSystem.write(dir.resolve(PluginRepository.MANIFEST_FILE_NAME)) {
            writeUtf8(
                """
                {
                  "id": "$id", "version": "$version", "name": { "en": "$dirName" },
                  "pasteTypes": ["text"], "runtime": "process", "output": "replaceClipboard",
                  "platforms": { "$platformKey": { "command": ["/bin/sh", "run.sh"], "requires": [$requiresJson] } }
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `scans plugin directories in name order and reports broken ones`() =
        runTest {
            writePlugin("b-plugin")
            writePlugin("a-plugin")
            fileSystem.createDirectories(pluginsDir.resolve("no-manifest"))
            fileSystem.write(pluginsDir.resolve("stray-file.txt")) { writeUtf8("x") }

            val repository = repository()
            repository.reload()
            val plugins = repository.plugins.value

            assertEquals(listOf("a-plugin", "b-plugin", "no-manifest"), plugins.map { it.directory.name })
            assertNull(plugins[0].problem)
            assertFalse(plugins[0].runnable, "plugins start disabled")
            assertNull(plugins[2].manifest)
            assertIs<PluginProblem.InvalidManifest>(plugins[2].problem)
        }

    @Test
    fun `duplicate ids keep the first directory`() =
        runTest {
            writePlugin("a", id = "com.example.same")
            writePlugin("b", id = "com.example.same")

            val repository = repository()
            repository.reload()

            assertNull(repository.plugins.value[0].problem)
            assertEquals(PluginProblem.DuplicateId("com.example.same"), repository.plugins.value[1].problem)
        }

    @Test
    fun `missing requirements block running but not enabling`() =
        runTest {
            writePlugin("needs-jq", requires = listOf("jq", "curl"))

            val repository = repository { requires -> requires.filter { it == "jq" } }
            repository.reload()
            val plugin = repository.plugins.value.single()
            repository.setEnabled(plugin, true)

            val enabled = repository.plugins.value.single()
            assertEquals(listOf("jq"), enabled.missingRequirements)
            assertTrue(enabled.enabled)
            assertFalse(enabled.runnable)
        }

    @Test
    fun `enabled state persists and is bound to the approved version`() =
        runTest {
            writePlugin("p", version = "1.0.0")
            val repository = repository()
            repository.reload()
            repository.setEnabled(repository.plugins.value.single(), true)
            assertTrue(
                repository.plugins.value
                    .single()
                    .runnable,
            )

            val reopened = repository()
            reopened.reload()
            assertTrue(
                reopened.plugins.value
                    .single()
                    .runnable,
            )

            writePlugin("p", version = "1.1.0")
            reopened.reload()
            assertFalse(
                reopened.plugins.value
                    .single()
                    .enabled,
                "a new version needs a new confirmation",
            )

            reopened.setEnabled(reopened.plugins.value.single(), true)
            reopened.reload()
            assertTrue(
                reopened.plugins.value
                    .single()
                    .runnable,
            )
        }

    @Test
    fun `setEnabled refuses plugins with problems`() =
        runTest {
            writePlugin("a", id = "com.example.same")
            writePlugin("b", id = "com.example.same")

            val repository = repository()
            repository.reload()

            val duplicatePlugin = repository.plugins.value[1]
            assertEquals(PluginProblem.DuplicateId("com.example.same"), duplicatePlugin.problem)

            repository.setEnabled(duplicatePlugin, true)
            assertFalse(repository.plugins.value[1].enabled)
            assertEquals(emptyList(), pluginStateDao.getAll())
        }

    @Test
    fun `a plugin that broke after being enabled can still be disabled`() =
        runTest {
            writePlugin("p")
            val repository = repository()
            repository.reload()
            repository.setEnabled(repository.plugins.value.single(), true)

            writePlugin("p", platformKey = "linux")
            repository.reload()
            val broken = repository.plugins.value.single()
            assertIs<PluginProblem.UnsupportedPlatform>(broken.problem)
            assertTrue(broken.enabled)

            repository.setEnabled(broken, false)
            assertFalse(
                repository.plugins.value
                    .single()
                    .enabled,
            )
            assertFalse(pluginStateDao.getAll().single().enabled)
        }

    @Test
    fun `disabling a duplicate leaves the first plugin with that id enabled`() =
        runTest {
            writePlugin("a", id = "com.example.same")
            val repository = repository()
            repository.reload()
            repository.setEnabled(repository.plugins.value.single(), true)

            writePlugin("b", id = "com.example.same")
            repository.reload()
            repository.setEnabled(repository.plugins.value[1], false)

            assertTrue(repository.plugins.value[0].runnable)
            assertTrue(pluginStateDao.getAll().single().enabled)
        }

    @Test
    fun `missing requirements are deduplicated`() =
        runTest {
            writePlugin("dup-reqs", requires = listOf("jq", "curl", "jq"))

            val repository = repository { requires -> requires.filter { it == "jq" } }
            repository.reload()

            val plugin = repository.plugins.value.single()
            assertEquals(listOf("jq"), plugin.missingRequirements)
        }
}
