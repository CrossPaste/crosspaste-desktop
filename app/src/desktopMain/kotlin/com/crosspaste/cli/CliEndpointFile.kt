package com.crosspaste.cli

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.writeText

/**
 * Discovery record for the local CLI. The CLI cannot derive the socket path
 * itself (macOS sun_path length limits force the socket into a short temp
 * directory), so the app declares it here — this file is the single source of
 * truth for "is the peer running and where is its socket" (design D5).
 */
@Serializable
data class CliEndpoint(
    val pid: Long,
    val socketPath: String,
    val apiVersion: Int,
    val appInstanceId: String,
)

class CliEndpointFile(
    private val userDataPathProvider: UserDataPathProvider,
) {

    private val logger = KotlinLogging.logger {}

    private val json = Json { prettyPrint = true }

    fun getPath(): Path =
        userDataPathProvider
            .resolve(CLI_ENDPOINT_FILE_NAME, AppFileType.USER)
            .toNioPath()

    /**
     * Atomic write (temp + rename) so the CLI never observes a partial file;
     * the temp file is restricted to the owner before it gains any content.
     */
    fun write(endpoint: CliEndpoint) {
        val path = getPath()
        val tempPath = path.resolveSibling("$CLI_ENDPOINT_FILE_NAME.tmp")
        tempPath.writeText(json.encodeToString(endpoint))
        restrictToOwner(tempPath)
        Files.move(
            tempPath,
            path,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    fun delete() {
        runCatching {
            Files.deleteIfExists(getPath())
        }.onFailure { e ->
            logger.warn(e) { "Failed to delete cli endpoint file" }
        }
    }

    private fun restrictToOwner(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }.onFailure {
            // Windows: no POSIX permissions; the user profile directory ACL applies
        }
    }
}
