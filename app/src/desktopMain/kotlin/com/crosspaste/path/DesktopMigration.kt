package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.isDirectory
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path

class DesktopMigration(
    private val configManager: ConfigManager,
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
        if (checkMigrationPath(migrationPath)) {
            runCatching {
                logger.info { "Migrating Data" }
                val originDataPath =
                    userDataPathProvider.resolve(appFileType = AppFileType.DATA)
                        .resolve(driverFactory.dbName)
                val migrationDataPath =
                    userDataPathProvider.resolve(fileName = null, appFileType = AppFileType.DATA) {
                        migrationPath
                    }.resolve(driverFactory.dbName)

                fileUtils.copyPath(originDataPath, migrationDataPath)
                logger.info { "Migrated Data to $migrationPath" }

                for (type in types) {
                    logger.info { "Migrating $type" }
                    val originTypePath = userDataPathProvider.resolve(appFileType = type)
                    val migrationTypePath =
                        userDataPathProvider.resolve(fileName = null, appFileType = type) {
                            migrationPath
                        }
                    fileUtils.copyPath(originTypePath, migrationTypePath)
                    logger.info { "Migrated $originTypePath to $migrationPath" }
                }

                configManager.updateConfig(
                    listOf("storagePath", "useDefaultStoragePath"),
                    listOf(migrationPath.toString(), false),
                )

                runCatching {
                    fileUtils.deleteFile(originDataPath)
                    logger.info { "Delete Data" }
                    for (type in types) {
                        val originTypePath = userDataPathProvider.resolve(appFileType = type)
                        fileUtils.fileSystem.deleteRecursively(originTypePath)
                        logger.info { "Delete $originTypePath" }
                    }
                }.onFailure { e ->
                    logger.error(e) { "Delete originPath fail" }
                }
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
                }
                throw e
            }
        }
    }

    private fun checkMigrationPath(path: Path): Boolean {
        val fileSystem = fileUtils.fileSystem

        if (!fileUtils.existFile(path)) {
            notificationManager.sendNotification(
                title = { it.getText("directory_not_exist") },
                messageType = MessageType.Error,
                duration = null,
            )
            return false
        }

        if (!path.isDirectory) {
            notificationManager.sendNotification(
                title = { it.getText("not_a_directory") },
                messageType = MessageType.Error,
                duration = null,
            )
            return false
        }

        val testFile = path / "permission_test_${System.currentTimeMillis()}.tmp"
        return try {
            fileSystem.write(testFile) {
                writeUtf8("permission_test")
            }
            fileUtils.deleteFile(testFile)
            true
        } catch (_: Exception) {
            notificationManager.sendNotification(
                title = { it.getText("no_write_permission") },
                messageType = MessageType.Error,
                duration = null,
            )
            false
        }
    }
}
