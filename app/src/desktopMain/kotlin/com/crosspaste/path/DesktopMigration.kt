package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.safeIsDirectory
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.DIRECTORY_SEPARATOR

class DesktopMigration(
    private val configManager: DesktopConfigManager,
    private val driverFactory: DriverFactory,
    private val notificationManager: NotificationManager,
    private val userDataPathProvider: UserDataPathProvider,
) {
    private val logger = KotlinLogging.logger {}

    private val fileUtils: FileUtils = getFileUtils()

    private val types: List<AppFileType> =
        listOf(
            AppFileType.FILE,
            AppFileType.IMAGE,
            AppFileType.HTML,
            AppFileType.RTF,
            AppFileType.ICON,
            AppFileType.FAVICON,
            AppFileType.FILE_EXT_ICON,
            AppFileType.VIDEO,
            AppFileType.TEMP,
            AppFileType.MARKETING,
        )

    fun migration(migrationPath: Path) {
        checkMigrationPath(migrationPath)?.let { errorMessage ->
            notificationManager.sendNotification(
                title = { it.getText(errorMessage) },
                messageType = MessageType.Error,
                duration = null,
            )
        } ?: run {
            // Resolved up front: they follow the configured storage path, which
            // points at the new location once the migration is committed.
            val originDataPath =
                userDataPathProvider
                    .resolve(appFileType = AppFileType.DATA)
                    .resolve(driverFactory.dbName)
            val originTypePaths = types.associateWith { userDataPathProvider.resolve(appFileType = it) }

            copyToMigrationPath(migrationPath, originDataPath, originTypePaths)

            // Only now that the config points at the copy may the origin go: an
            // interrupted or failed delete then just leaves stale files behind,
            // never a config pointing at a deleted store.
            runCatching {
                fileUtils.deleteFile(originDataPath)
                logger.info { "Delete Data" }
                for (originTypePath in originTypePaths.values) {
                    fileUtils.fileSystem.deleteRecursively(originTypePath)
                    logger.info { "Delete $originTypePath" }
                }
            }.onFailure { e ->
                logger.error(e) { "Delete originPath fail" }
            }
        }
    }

    /**
     * Copies the data over and points the config at it. On failure the copy is
     * removed and the error rethrown, leaving the origin store in use untouched.
     */
    private fun copyToMigrationPath(
        migrationPath: Path,
        originDataPath: Path,
        originTypePaths: Map<AppFileType, Path>,
    ) {
        runCatching {
            logger.info { "Migrating Data" }
            val migrationDataPath =
                userDataPathProvider
                    .resolve(fileName = null, appFileType = AppFileType.DATA) {
                        migrationPath
                    }.resolve(driverFactory.dbName)

            fileUtils.copyPath(originDataPath, migrationDataPath)
            logger.info { "Migrated Data to $migrationPath" }

            for ((type, originTypePath) in originTypePaths) {
                logger.info { "Migrating $type" }
                val migrationTypePath =
                    userDataPathProvider.resolve(fileName = null, appFileType = type) {
                        migrationPath
                    }
                fileUtils.copyPath(originTypePath, migrationTypePath)
                logger.info { "Migrated $originTypePath to $migrationPath" }
            }
            configManager.updateConfigDurably(
                listOf("storagePath", "useDefaultStoragePath"),
                listOf(migrationPath.toString(), false),
            )
        }.onFailure { e ->
            logger.error(e) { "Migrated fail" }
            runCatching {
                val fileSystem = fileUtils.fileSystem
                fileSystem.list(migrationPath).forEach { subPath ->
                    if (fileSystem.metadata(subPath).isDirectory) {
                        fileSystem.deleteRecursively(subPath)
                    } else {
                        fileSystem.delete(subPath)
                    }
                }
            }.onFailure { cleanupError ->
                logger.warn(cleanupError) { "Failed to clean up migration path after failure" }
            }
            throw e
        }
    }

    fun checkMigrationPath(migrationPath: Path): String? {
        val fileSystem = fileUtils.fileSystem
        val currentStoragePath = userDataPathProvider.getUserDataPath()

        if (!fileUtils.existFile(migrationPath)) {
            return "directory_not_exist"
        }

        if (!migrationPath.safeIsDirectory) {
            return "not_a_directory"
        }

        var migrationPathString = migrationPath.toString()
        if (!migrationPathString.endsWith(DIRECTORY_SEPARATOR)) {
            migrationPathString = migrationPathString + DIRECTORY_SEPARATOR
        }
        val currentStoragePathString =
            currentStoragePath.toString()

        return if (currentStoragePathString
                .startsWith(migrationPathString)
        ) {
            "cant_select_child_directory"
        } else if (migrationPathString
                .startsWith(currentStoragePathString)
        ) {
            "cant_select_parent_directory"
        } else if (fileUtils
                .listFiles(migrationPath) { it ->
                    !it.name.startsWith(".")
                }.isNotEmpty()
        ) {
            "directory_not_empty"
        } else {
            val testFile = migrationPath / "permission_test_${System.currentTimeMillis()}.tmp"
            try {
                fileSystem.write(testFile) {
                    writeUtf8("permission_test")
                }
                null
            } catch (_: Exception) {
                "no_write_permission"
            } finally {
                runCatching { fileUtils.deleteFile(testFile) }
            }
        }
    }
}
