package com.crosspaste.config

import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getPlatformUtils
import com.crosspaste.utils.safeIsDirectory
import okio.Path
import okio.Path.Companion.DIRECTORY_SEPARATOR
import okio.Path.Companion.toPath
import kotlin.random.Random

/**
 * Directory that received files above `maxBackupFileSize` are written to.
 *
 * An empty [AppConfig.largeFileDestinationPath] resolves to the system Downloads
 * folder, so a user who never touched the setting keeps following the system
 * location even if it moves later.
 */
fun AppConfig.resolveLargeFileDestination(): Path =
    largeFileDestinationPath
        .takeIf { it.isNotBlank() }
        ?.toPath(normalize = true)
        ?: getPlatformUtils().getSystemDownloadDir()

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

    if (destination.withTrailingSeparator().startsWith(managedStoragePath.withTrailingSeparator())) {
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

private fun Path.withTrailingSeparator(): String =
    toString().let {
        if (it.endsWith(DIRECTORY_SEPARATOR)) it else it + DIRECTORY_SEPARATOR
    }
