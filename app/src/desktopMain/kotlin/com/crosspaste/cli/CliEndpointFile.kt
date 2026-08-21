package com.crosspaste.cli

import com.crosspaste.app.AppFileType
import com.crosspaste.path.LinuxAppPathProvider.Companion.LOCAL
import com.crosspaste.path.LinuxAppPathProvider.Companion.SHARE
import com.crosspaste.path.PlatformUserDataPathProvider.Companion.CROSSPASTE_DIR_NAME
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getSystemProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
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

/**
 * Resolves where a DEVELOPMENT app should advertise itself for PATH-installed
 * CLIs (see [CLI_DEV_ENDPOINT_FILE_NAME]); null in any other environment so
 * production and test apps never touch the installed app's directory.
 */
fun devCliEndpointPointerPath(platform: Platform): Path? =
    if (getAppEnvUtils().isDevelopment()) {
        installedAppDefaultUserDataDir(platform, Paths.get(getSystemProperty().get("user.home")))
            ?.resolve(CLI_DEV_ENDPOINT_FILE_NAME)
    } else {
        null
    }

/**
 * Pure mirror of the installed app's default user-data locations (the same
 * table the CLI's NativePlatformPathProvider hardcodes). Deliberately NOT the
 * production PlatformUserDataPathProvider: its getters have side effects a
 * dev run must never inflict on the installed app's data — macOS creates the
 * directory, and Linux performs the real shard→share data migration.
 */
internal fun installedAppDefaultUserDataDir(
    platform: Platform,
    userHome: Path,
): Path? =
    if (platform.isWindows()) {
        userHome.resolve(CROSSPASTE_DIR_NAME)
    } else if (platform.isMacos()) {
        userHome.resolve("Library").resolve("Application Support").resolve("CrossPaste")
    } else if (platform.isLinux()) {
        userHome.resolve(LOCAL).resolve(SHARE).resolve(CROSSPASTE_DIR_NAME)
    } else {
        null
    }

class CliEndpointFile(
    private val userDataPathProvider: UserDataPathProvider,
    private val devPointerPath: Path? = null,
) {

    private val logger = KotlinLogging.logger {}

    private val json = Json { prettyPrint = true }

    fun getPath(): Path =
        userDataPathProvider
            .resolve(CLI_ENDPOINT_FILE_NAME, AppFileType.USER)
            .toNioPath()

    fun write(endpoint: CliEndpoint) {
        writeAtomically(getPath(), endpoint)
        devPointerPath?.let { pointer ->
            // Best effort: failing to advertise the dev instance must not
            // keep the CLI server itself from starting. Creating the default
            // directory here is accepted even though on Linux a pre-existing
            // (empty) target makes an installed shard-era app skip its data
            // migration — that needs data untouched by any release of the
            // last years, and the old directory stays intact either way.
            runCatching {
                Files.createDirectories(pointer.parent)
                writeAtomically(pointer, endpoint)
            }.onFailure { e ->
                logger.warn(e) { "Failed to write dev cli endpoint pointer $pointer" }
            }
        }
    }

    fun delete() {
        deleteQuietly(getPath())
        devPointerPath?.let { deleteQuietly(it) }
    }

    /**
     * Atomic write (temp + rename) so the CLI never observes a partial file.
     * The randomly named temp file is created with owner-only permissions
     * BEFORE any content is written (createTempFile applies the mode at
     * open time), so there is no window where other users could read it.
     */
    private fun writeAtomically(
        path: Path,
        endpoint: CliEndpoint,
    ) {
        val tempPath = createOwnerOnlyTempFile(path.parent)
        try {
            tempPath.writeText(json.encodeToString(endpoint))
            Files.move(
                tempPath,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(tempPath) }
            throw e
        }
    }

    private fun deleteQuietly(path: Path) {
        runCatching {
            Files.deleteIfExists(path)
        }.onFailure { e ->
            logger.warn(e) { "Failed to delete cli endpoint file $path" }
        }
    }

    private fun createOwnerOnlyTempFile(dir: Path): Path {
        val posix =
            dir.fileSystem.supportedFileAttributeViews().contains("posix")
        return if (posix) {
            Files.createTempFile(
                dir,
                CLI_ENDPOINT_FILE_NAME,
                ".tmp",
                PosixFilePermissions.asFileAttribute(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                ),
            )
        } else {
            // Windows: no POSIX permissions; the user profile directory ACL applies
            Files.createTempFile(dir, CLI_ENDPOINT_FILE_NAME, ".tmp")
        }
    }
}
