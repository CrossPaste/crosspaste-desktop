package com.crosspaste.plugin

import com.crosspaste.platform.Platform
import com.crosspaste.test.IntegrationTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/** Spawns real `/bin/sh` processes, so POSIX only and in the integration tier. */
@IntegrationTest
class ProcessPluginExecutorIntegrationTest {

    private val pluginDir: File = Files.createTempDirectory("plugin 插件").toFile()

    @AfterTest
    fun cleanup() {
        pluginDir.deleteRecursively()
    }

    private fun script(body: String): List<String> {
        File(pluginDir, "run.sh").writeText(body)
        return listOf("/bin/sh", "run.sh")
    }

    @Test
    fun `round trips UTF-8 through stdin and stdout in the plugin directory`() {
        if (File.separatorChar == '\\') return
        val executor = ProcessPluginExecutor(cliBinaryPath = { "/opt/crosspaste-cli" }, language = { "en" })
        val command =
            script(
                """
                cat
                printf '\n%s\n' "${'$'}PWD"
                printf '%s\n' "${'$'}CROSSPASTE_CLI"
                echo oops >&2
                """.trimIndent(),
            )

        val result =
            runBlocking {
                executor.execute(pluginDir.toOkioPath(), command, """{"content":"héllo 你好"}""")
            }

        assertEquals(0, result.exitCode)
        assertEquals(
            listOf("""{"content":"héllo 你好"}""", pluginDir.canonicalPath, "/opt/crosspaste-cli"),
            result.stdout.trimEnd('\n').lines(),
        )
        assertEquals("oops\n", result.stderr)
    }

    @Test
    fun `a plugin that ignores stdin still completes`() {
        if (File.separatorChar == '\\') return
        val executor = ProcessPluginExecutor(cliBinaryPath = { null }, language = { "en" })
        val command = script("echo done")

        val result = runBlocking { executor.execute(pluginDir.toOkioPath(), command, "x".repeat(1 shl 20)) }

        assertEquals(0, result.exitCode)
        assertEquals("done\n", result.stdout)
    }

    @Test
    fun `the sample plugin in doc runs as documented`() {
        if (File.separatorChar == '\\' || findMissingExecutables(listOf("jq")).isNotEmpty()) return
        val sampleDir = File("../doc/plugins/uppercase").canonicalFile
        val manifest =
            Json { ignoreUnknownKeys = true }
                .decodeFromString(PluginManifest.serializer(), File(sampleDir, "manifest.json").readText())
        val environment =
            PluginEnvironment(
                platform = Platform(Platform.MACOS, "aarch64", 64, "15"),
                appVersion = "2.3.0",
                runtimes = setOf(PluginEnvironment.RUNTIME_PROCESS),
            )
        assertNull(checkPluginCompatibility(manifest, environment))

        val result =
            runBlocking {
                ProcessPluginExecutor(cliBinaryPath = { null }, language = { "en" }).execute(
                    directory = sampleDir.toOkioPath(),
                    command = manifest.platforms.getValue("macos").command,
                    input = """{"id":1,"typeName":"text","content":"x","rawContent":"hello crosspaste"}""",
                )
            }

        assertEquals(0, result.exitCode, result.stderr)
        assertEquals("HELLO CROSSPASTE\n", result.stdout)
    }

    @Test
    fun `timeout kills the whole process tree`() {
        if (File.separatorChar == '\\') return
        val executor = ProcessPluginExecutor(cliBinaryPath = { null }, language = { "en" }, timeout = 1.seconds)
        val pidFile = File(pluginDir, "child.pid")
        // The grandchild holds stdout open: without the tree kill the run would never end
        val command = script("sleep 30 & echo ${'$'}! > child.pid; wait")

        var exitCode: Int? = -1
        val elapsed =
            measureTime {
                exitCode =
                    runBlocking { executor.execute(pluginDir.toOkioPath(), command, "") }.exitCode
            }

        assertNull(exitCode)
        assertTrue(elapsed < 10.seconds, "took $elapsed")
        val childPid = pidFile.readText().trim().toLong()
        ProcessHandle.of(childPid).ifPresent { it.onExit().get(5, TimeUnit.SECONDS) }
        assertTrue(ProcessHandle.of(childPid).map { !it.isAlive }.orElse(true), "grandchild survived")
    }

    @Test
    fun `grandchild holding pipe open does not hang execution after main process exits`() {
        if (File.separatorChar == '\\') return
        val executor =
            ProcessPluginExecutor(cliBinaryPath = { null }, language = { "en" }, pipeDrainTimeout = 1.seconds)
        // Grandchild sleeps 30s holding stdout open, but main process exits immediately
        val command = script("sleep 30 & echo done")

        val result = runBlocking { executor.execute(pluginDir.toOkioPath(), command, "") }

        assertEquals(0, result.exitCode)
        assertEquals("done\n", result.stdout)
    }
}
