package com.crosspaste.plugin

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginRequirementsTest {

    private val binDir: File = Files.createTempDirectory("bin").toFile()

    @AfterTest
    fun cleanup() {
        binDir.deleteRecursively()
    }

    private fun createFile(
        name: String,
        executable: Boolean,
    ): File =
        File(binDir, name).apply {
            writeText("")
            setExecutable(executable)
        }

    @Test
    fun `finds executables on PATH and by explicit path`() {
        createFile("tool", executable = true)
        val explicit = createFile("explicit", executable = true)

        val missing =
            findMissingExecutables(
                requires = listOf("tool", "absent", explicit.path),
                env = mapOf("PATH" to "/nonexistent${File.pathSeparator}${binDir.path}"),
                isWindows = false,
            )

        assertEquals(listOf("absent"), missing)
    }

    @Test
    fun `non executable files do not count on posix`() {
        createFile("plain", executable = false)

        val missing =
            findMissingExecutables(listOf("plain"), mapOf("PATH" to binDir.path), isWindows = false)

        assertEquals(listOf("plain"), missing)
    }

    @Test
    fun `windows lookup applies PATHEXT`() {
        createFile("curl.EXE", executable = false)

        val missing =
            findMissingExecutables(
                requires = listOf("curl", "jq"),
                env = mapOf("Path" to binDir.path, "PATHEXT" to ".COM;.EXE"),
                isWindows = true,
            )

        assertEquals(listOf("jq"), missing)
    }
}
