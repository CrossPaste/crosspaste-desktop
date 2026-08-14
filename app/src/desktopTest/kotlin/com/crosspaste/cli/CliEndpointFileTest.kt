package com.crosspaste.cli

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliEndpointFileTest {

    private fun createEndpointFile(tempDir: java.nio.file.Path): CliEndpointFile {
        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns DesktopAppConfig(language = "en")
        val platformProvider =
            object : PlatformUserDataPathProvider {
                override fun getUserDefaultStoragePath() = tempDir.toFile().toOkioPath()
            }
        return CliEndpointFile(UserDataPathProvider(configManager, platformProvider))
    }

    private val endpoint =
        CliEndpoint(
            pid = 42L,
            socketPath = "/tmp/crosspaste-user/cli.sock",
            apiVersion = CLI_API_VERSION,
            appInstanceId = "test-instance",
        )

    @Test
    fun `write creates a parseable endpoint file without temp residue`() {
        val tempDir = Files.createTempDirectory("cli-endpoint-test")
        val endpointFile = createEndpointFile(tempDir)

        endpointFile.write(endpoint)

        val path = endpointFile.getPath()
        assertTrue(path.exists())
        val parsed = Json.decodeFromString<CliEndpoint>(path.readText())
        assertEquals(endpoint, parsed)
        assertEquals(
            listOf(path.fileName.toString()),
            tempDir.listDirectoryEntries().map { it.fileName.toString() },
            "temp file must not survive the atomic rename",
        )
    }

    @Test
    fun `write restricts the file to the owner on posix`() {
        val tempDir = Files.createTempDirectory("cli-endpoint-test")
        val endpointFile = createEndpointFile(tempDir)

        endpointFile.write(endpoint)

        val permissions =
            runCatching {
                Files.getPosixFilePermissions(endpointFile.getPath())
            }.getOrNull() ?: return // Windows: no POSIX permissions to assert
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            permissions,
        )
    }

    @Test
    fun `write replaces an existing endpoint file`() {
        val tempDir = Files.createTempDirectory("cli-endpoint-test")
        val endpointFile = createEndpointFile(tempDir)

        endpointFile.write(endpoint.copy(pid = 1L))
        endpointFile.write(endpoint.copy(pid = 2L))

        val parsed = Json.decodeFromString<CliEndpoint>(endpointFile.getPath().readText())
        assertEquals(2L, parsed.pid)
    }

    @Test
    fun `delete removes the endpoint file and tolerates a missing one`() {
        val tempDir = Files.createTempDirectory("cli-endpoint-test")
        val endpointFile = createEndpointFile(tempDir)

        endpointFile.write(endpoint)
        endpointFile.delete()
        assertFalse(endpointFile.getPath().exists())

        // Deleting again must not throw
        endpointFile.delete()
    }
}
