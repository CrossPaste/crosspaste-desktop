package com.crosspaste.config

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getPlatformUtils
import com.crosspaste.utils.safeIsDirectory
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.DIRECTORY_SEPARATOR
import okio.Path.Companion.toPath
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Directory that received files above `maxBackupFileSize` are written to.
 *
 * An empty [AppConfig.largeFileDestinationPath] resolves to the system Downloads
 * folder, so a user who never touched the setting keeps following the system
 * location even if it moves later.
 *
 * This is the value to SHOW. Use [resolveLargeFileDestinationForReceive] when
 * actually writing files, because a configured directory can disappear between
 * being picked and being used.
 */
fun AppConfig.resolveLargeFileDestination(): Path =
    largeFileDestinationPath
        .takeIf { it.isNotBlank() }
        ?.toPath(normalize = true)
        ?: getPlatformUtils().getSystemDownloadDir()

/**
 * Same as [resolveLargeFileDestination], but falls back to the system Downloads
 * folder when the configured directory is gone — a deleted folder or an external
 * volume whose mount point no longer exists.
 *
 * Falling back rather than failing the receive keeps the file: the paste lands in
 * a predictable default location instead of being dropped. Without this check the
 * receive path would call `autoCreateDir` and recreate the missing directory,
 * which for an unmounted volume means writing onto the local disk underneath the
 * mount point, where the files become invisible once the volume is mounted again.
 *
 * Note this cannot detect a mount point that still exists as an empty directory
 * after its volume was unmounted; that case still writes under the mount point.
 */
fun AppConfig.resolveLargeFileDestinationForReceive(managedStoragePath: Path): Path {
    val configured =
        largeFileDestinationPath.takeIf { it.isNotBlank() } ?: return getPlatformUtils().getSystemDownloadDir()
    val destination = configured.toPath(normalize = true)
    return if (validateLargeFileDestination(destination, managedStoragePath) == null) {
        destination
    } else {
        val fallback = getPlatformUtils().getSystemDownloadDir()
        logger.warn {
            "Large file destination $destination is unusable or inside managed storage, falling back to $fallback"
        }
        fallback
    }
}

/**
 * Rejects a directory that cannot hold received files, returning the copywriter
 * key of the reason or null when the directory is usable.
 *
 * Unlike the storage-path migration check this deliberately accepts a non-empty
 * directory — Downloads and any folder a user would pick for received files
 * normally already has files in it.
 */
fun validateLargeFileDestination(
    destination: Path,
    managedStoragePath: Path,
): String? {
    val fileUtils = getFileUtils()

    if (!fileUtils.existFile(destination)) {
        return "directory_not_exist"
    }

    if (!destination.safeIsDirectory) {
        return "not_a_directory"
    }

    if (isInside(destination, managedStoragePath)) {
        // Inside managed storage the cleanup policy would delete the file, which is
        // the behavior this setting exists to get away from.
        return "cant_select_child_directory"
    }

    val probe = destination / "crosspaste_write_test_${Random.nextLong()}.tmp"
    return try {
        fileUtils.fileSystem.write(probe) { writeUtf8("crosspaste_write_test") }
        null
    } catch (_: Exception) {
        "no_write_permission"
    } finally {
        runCatching { fileUtils.deleteFile(probe) }
    }
}

/**
 * Whether [child] sits at or below [parent].
 *
 * Symlinks are resolved first so a linked path cannot slip past the check, and the
 * comparison ignores case: on macOS and Windows a differently-cased spelling
 * reaches the same directory, and on a case-sensitive filesystem the worst this
 * costs is rejecting a directory whose name differs from managed storage only by
 * case — which is the safe direction to be wrong in.
 */
internal fun isInside(
    child: Path,
    parent: Path,
): Boolean {
    val canonicalChild = canonicalizeOrSelf(child).withTrailingSeparator().lowercase()
    val canonicalParent = canonicalizeOrSelf(parent).withTrailingSeparator().lowercase()
    return canonicalChild.startsWith(canonicalParent)
}

private fun canonicalizeOrSelf(path: Path): Path =
    runCatching { getFileUtils().fileSystem.canonicalize(path) }.getOrDefault(path)

private fun Path.withTrailingSeparator(): String =
    toString().let {
        if (it.endsWith(DIRECTORY_SEPARATOR)) it else it + DIRECTORY_SEPARATOR
    }
