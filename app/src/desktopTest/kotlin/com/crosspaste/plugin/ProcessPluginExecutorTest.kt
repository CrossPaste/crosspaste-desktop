package com.crosspaste.plugin

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessPluginExecutorTest {

    private val pluginDir = File("plugins", "demo").absoluteFile

    @Test
    fun `relative executables with a separator resolve against the plugin directory`() {
        assertEquals(
            listOf(File(pluginDir, "bin/tool").path, "run.sh"),
            resolvePluginCommand(pluginDir, listOf("bin/tool", "run.sh")),
        )
        assertEquals(
            listOf(File(pluginDir, "./tool").path),
            resolvePluginCommand(pluginDir, listOf("./tool")),
        )
    }

    @Test
    fun `bare and absolute executables are left for the OS to resolve`() {
        assertEquals(
            listOf("powershell", "-File", "run.ps1"),
            resolvePluginCommand(pluginDir, listOf("powershell", "-File", "run.ps1")),
        )
        val absolute = File("/bin/sh").absolutePath
        assertEquals(listOf(absolute, "run.sh"), resolvePluginCommand(pluginDir, listOf(absolute, "run.sh")))
    }

    @Test
    fun `empty command returns empty list`() {
        assertEquals(emptyList(), resolvePluginCommand(pluginDir, emptyList()))
    }

    @Test
    fun `environment drops launcher variables and injects plugin variables`() {
        val env =
            mutableMapOf(
                "PATH" to "/usr/bin",
                "JAVA_MAIN_CLASS_123" to "com.crosspaste.CrossPaste",
                "JAVA_STARTED_ON_FIRST_THREAD_123" to "1",
                "CROSSPASTE_CLI" to "stale",
            )

        configurePluginEnvironment(env, "/Applications/CrossPaste.app/Contents/bin/crosspaste", "zh_hant")

        assertEquals("/usr/bin", env["PATH"])
        assertEquals("utf-8", env["PYTHONIOENCODING"])
        assertEquals("/Applications/CrossPaste.app/Contents/bin/crosspaste", env["CROSSPASTE_CLI"])
        assertEquals("zh_hant", env["CROSSPASTE_LANGUAGE"])
        assertFalse(env.keys.any { it.startsWith("JAVA_") })

        assertFalse("CROSSPASTE_ACTION" in env)

        configurePluginEnvironment(env, null, "en", action = "zh-CN")
        assertEquals("zh-CN", env["CROSSPASTE_ACTION"])

        configurePluginEnvironment(env, null, "en")
        assertFalse("CROSSPASTE_CLI" in env)
        assertFalse("CROSSPASTE_ACTION" in env)
    }

    @Test
    fun `extra PATH dirs are appended once after the inherited PATH`() {
        val env = mutableMapOf("PATH" to listOf("/usr/bin", "/usr/local/bin").joinToString(File.pathSeparator))

        configurePluginEnvironment(env, null, "en", extraPathDirs = listOf("/opt/homebrew/bin", "/usr/local/bin"))

        assertEquals(
            listOf("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin").joinToString(File.pathSeparator),
            env["PATH"],
        )
    }

    @Test
    fun `output decoding strips the BOM and normalizes CRLF`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "你好\r\nworld\r\n".encodeToByteArray()
        assertEquals("你好\nworld\n", decodePluginOutput(bytes))
    }

    @Test
    fun `pipe capture keeps output up to the limit and flags the rest`() {
        val capture = PipeCapture(limit = 4)
        capture.drain(ByteArrayInputStream("hello world".encodeToByteArray()))

        assertEquals("hell", capture.bytes().decodeToString())
        assertTrue(capture.truncated)
    }

    @Test
    fun `pipe capture within the limit is not truncated`() {
        val capture = PipeCapture(limit = 64)
        capture.drain(ByteArrayInputStream("hello".encodeToByteArray()))

        assertEquals("hello", capture.bytes().decodeToString())
        assertFalse(capture.truncated)
    }
}
